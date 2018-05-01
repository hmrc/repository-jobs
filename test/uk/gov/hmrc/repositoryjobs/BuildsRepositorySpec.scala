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
      val existingBuild1 = generateBuild(suffix = 1)
      val existingBuild2 = generateBuild(suffix = 2)

      repository.insert(existingBuild1).futureValue
      repository.insert(existingBuild2).futureValue
      repository.count.futureValue shouldBe 2

      val updatedBuild1 = existingBuild1.copy(result = Some("result1Updated"))
      val newBuild      = generateBuild(suffix       = 3)

      repository.persist(Seq(updatedBuild1, newBuild)).futureValue shouldBe UpdateResult(nSuccesses = 2, nFailures = 0)

      repository.findAll().futureValue should contain theSameElementsAs Seq(updatedBuild1, existingBuild2, newBuild)
    }

    "do nothing when there are no jobs given" in new Setup {
      val existingBuild1 = generateBuild(suffix = 1)
      val existingBuild2 = generateBuild(suffix = 2)

      repository.insert(existingBuild1).futureValue
      repository.insert(existingBuild2).futureValue
      repository.count.futureValue shouldBe 2

      repository.persist(Nil).futureValue shouldBe UpdateResult(nSuccesses = 0, nFailures = 0)

      repository.findAll().futureValue should contain theSameElementsAs Seq(existingBuild1, existingBuild2)
    }

    "add another record if there is another build for the same job" in new Setup {
      val build = generateBuild(suffix = 1)

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

      val build1        = generateBuild(suffix  = 1)
      val build2        = generateBuild(suffix  = 2)
      val build2Updated = build2.copy(timestamp = Some(System.currentTimeMillis()))

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
  }

  "getForRepository" should {

    "return all builds for the given repository" in new Setup {
      val build                     = generateBuild(suffix = 1)
      val anotherBuildForTheSameJob = build.copy(timestamp = Some(System.currentTimeMillis()), buildNumber = Some(2))
      val buildForDifferentJob      = generateBuild(suffix = 2)

      repository.insert(build).futureValue
      repository.insert(anotherBuildForTheSameJob).futureValue
      repository.insert(buildForDifferentJob).futureValue
      repository.count.futureValue shouldBe 3

      val builds = repository
        .getForRepository(
          build.repositoryName.getOrElse(throw new IllegalStateException("repository name should be defined"))
        )
        .futureValue

      builds should contain theSameElementsAs Seq(build, anotherBuildForTheSameJob)
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

  private def generateBuild(suffix: Int) = Build(
    repositoryName = Some(s"repo$suffix"),
    jobName        = Some(s"job$suffix"),
    jobUrl         = Some(s"jobUrl$suffix"),
    buildNumber    = Some(suffix),
    result         = Some(s"result$suffix"),
    timestamp      = Some(System.currentTimeMillis()),
    duration       = Some(suffix),
    buildUrl       = Some(s"buildUrl$suffix"),
    builtOn        = Some(s"agent$suffix")
  )
}
