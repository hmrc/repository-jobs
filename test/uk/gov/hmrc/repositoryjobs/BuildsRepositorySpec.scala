/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.repositoryjobs

import org.scalacheck.Gen
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import play.api.libs.json.{JsValue, Json}
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.indexes.IndexType.Descending
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}

import scala.concurrent.ExecutionContext.Implicits.global

class BuildsRepositorySpec extends WordSpec with MongoSpecSupport with ScalaFutures with IntegrationPatience {

  "Builds repository" should {
    "ensure indexes are created" in new Setup {
      val indexes = repository.collection.indexesManager.list().futureValue

      indexes.map(index => index.key -> index.background) should contain allOf (
        Seq("repositoryName" -> Descending) -> true,
        Seq("jobName"        -> Descending, "timestamp" -> Descending) -> true
      )
    }
  }

  "persist" should {

    "updates existing jobs and insert new ones" in new Setup {
      val existingBuild1 = builds.generateOne
      val existingBuild2 = builds.generateOne

      repository.insert(existingBuild1).futureValue
      repository.insert(existingBuild2).futureValue
      repository.count.futureValue shouldBe 2

      val updatedBuild1 = existingBuild1.copy(result = Some("result1Updated"))
      val newBuild      = builds.generateOne

      repository.persist(Seq(updatedBuild1, newBuild)).futureValue shouldBe UpdateResult(nSuccesses = 2, nFailures = 0)

      repository.findAll().futureValue should contain theSameElementsAs Seq(updatedBuild1, existingBuild2, newBuild)
    }

    "do nothing when there are no jobs given" in new Setup {
      val existingBuild1 = builds.generateOne
      val existingBuild2 = builds.generateOne

      repository.insert(existingBuild1).futureValue
      repository.insert(existingBuild2).futureValue
      repository.count.futureValue shouldBe 2

      repository.persist(Nil).futureValue shouldBe UpdateResult(nSuccesses = 0, nFailures = 0)

      repository.findAll().futureValue should contain theSameElementsAs Seq(existingBuild1, existingBuild2)
    }

    "add another record if there is another build for the same job" in new Setup {
      val build = builds.generateOne

      repository.insert(build).futureValue
      repository.count.futureValue shouldBe 1

      val anotherBuildForTheSameJob = build.copy(timestamp = Some(System.currentTimeMillis()), buildNumber = Some(2))

      repository.persist(Seq(build, anotherBuildForTheSameJob)).futureValue shouldBe UpdateResult(
        nSuccesses = 2,
        nFailures  = 0
      )

      repository.findAll().futureValue should contain theSameElementsAs Seq(build, anotherBuildForTheSameJob)
    }

    "save builds until the first failing build" in new Setup {

      val build1 = builds.generateOne.copy(jobName = Some("job1"))
      val build2 = builds.generateOne.copy(jobName = Some("job2"))

      val build2Updated = build2.copy(buildNumber = build2.buildNumber.map(_ + 1))

      def updateQueryFailing(build: Build): Build => JsValue = {
        case buildToSerialize if buildToSerialize == build => Json.obj("_id" -> 0)
        case buildToSerialize                              => Json.toJson(buildToSerialize)
      }
      override val repository = new BuildsRepository(mongoComponent, updateQueryFailing(build2Updated))

      repository.insert(build2).futureValue
      repository.count.futureValue shouldBe 1

      repository
        .persist(Seq(build1, build2Updated))
        .futureValue shouldBe UpdateResult(
        nSuccesses = 1,
        nFailures  = 1
      )

      repository.findAll().futureValue should contain theSameElementsAs Seq(build1, build2)
    }

    "allow to save high number of build objects" in new Setup {

      val builds = buildsList(1100).generateOne

      repository
        .persist(builds)
        .futureValue shouldBe UpdateResult(
        nSuccesses = builds.size,
        nFailures  = 0
      )

      repository.count.futureValue shouldBe builds.size
    }
  }

  "getForRepository" should {

    "return all builds for the given repository" in new Setup {
      val build                     = builds.generateOne
      val anotherBuildForTheSameJob = build.copy(timestamp = Some(System.currentTimeMillis()), buildNumber = Some(2))
      val buildForDifferentJob      = builds.generateOne

      repository.insert(build).futureValue
      repository.insert(anotherBuildForTheSameJob).futureValue
      repository.insert(buildForDifferentJob).futureValue
      repository.count.futureValue shouldBe 3

      val buildsForRepository = repository
        .getForRepository(build.repositoryName.get)
        .futureValue

      buildsForRepository should contain theSameElementsAs Seq(build, anotherBuildForTheSameJob)
    }

    "return no builds if there are not jobs for the given repository" in new Setup {

      val builds = repository.getForRepository("unknown repository").futureValue

      builds shouldBe empty
    }
  }

  private val mongoComponent: ReactiveMongoComponent = new ReactiveMongoComponent {
    override val mongoConnector: MongoConnector = mongoConnectorForTest
  }

  private trait Setup {
    mongo().drop().futureValue

    val repository = new BuildsRepository(mongoComponent)
  }

  private def strings(minLength: Int, maxLength: Int): Gen[String] =
    for {
      length <- Gen.chooseNum(minLength, maxLength)
      chars  <- Gen.listOfN(length, Gen.alphaNumChar)
    } yield chars.mkString

  private def positiveInts(max: Int): Gen[Int] = Gen.chooseNum(1, max)

  private val builds: Gen[Build] = for {
    repositoryName <- strings(5, 20)
    jobName        <- strings(5, 20)
    jobUrl         <- strings(5, 40)
    buildNumber    <- positiveInts(9999)
    result         <- strings(5, 10)
    timestamp      <- Gen.const(System.currentTimeMillis())
    duration       <- positiveInts(99999)
    buildUrl       <- strings(5, 40)
    builtOn        <- strings(5, 10)
  } yield
    Build(
      Some(repositoryName),
      Some(jobName),
      Some(jobUrl),
      Some(buildNumber),
      Some(result),
      Some(timestamp),
      Some(duration),
      Some(buildUrl),
      Some(builtOn)
    )

  private def buildsList(size: Int): Gen[Seq[Build]] = Gen.listOfN(size, builds)

  private implicit class GenOps[T](generator: Gen[T]) {
    lazy val generateOne: T = generator.sample.getOrElse(generateOne)
  }
}
