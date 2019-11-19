/*
 * Copyright 2019 HM Revenue & Customs
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

import org.mongodb.scala.model.IndexModel
import org.scalacheck.Gen
import org.scalatest.Matchers._
import org.scalatest.WordSpec
import org.scalatest.concurrent.IntegrationPatience
import uk.gov.hmrc.mongo.test.DefaultMongoCollectionSupport

class BuildsRepositorySpec extends WordSpec with DefaultMongoCollectionSupport with IntegrationPatience {

  "persist" should {

    "updates existing jobs and insert new ones" in {
      val existingBuild1 = builds.generateOne
      val existingBuild2 = builds.generateOne

      repository.collection.insertOne(existingBuild1).toFuture.futureValue
      repository.collection.insertOne(existingBuild2).toFuture.futureValue
      repository.collection.countDocuments.toFuture.futureValue shouldBe 2

      val updatedBuild1 = existingBuild1.copy(result = Some("result1Updated"))
      val newBuild = builds.generateOne

      repository.persist(Seq(updatedBuild1, newBuild)).futureValue shouldBe UpdateResult(nSuccesses = 2, nFailures = 0)

      repository.collection.find().toFuture.futureValue should contain theSameElementsAs Seq(updatedBuild1, existingBuild2, newBuild)
    }

    "do nothing when there are no jobs given" in {
      val existingBuild1 = builds.generateOne
      val existingBuild2 = builds.generateOne

      repository.collection.insertOne(existingBuild1).toFuture.futureValue
      repository.collection.insertOne(existingBuild2).toFuture.futureValue
      repository.collection.countDocuments.toFuture.futureValue shouldBe 2

      repository.persist(Nil).futureValue shouldBe UpdateResult(nSuccesses = 0, nFailures = 0)

      repository.collection.find().toFuture.futureValue should contain theSameElementsAs Seq(existingBuild1, existingBuild2)
    }

    "add another record if there is another build for the same job" in {
      val build = builds.generateOne

      repository.collection.insertOne(build).toFuture.futureValue
      repository.collection.countDocuments.toFuture.futureValue shouldBe 1

      val anotherBuildForTheSameJob = build.copy(timestamp = Some(System.currentTimeMillis()), buildNumber = Some(2))

      repository.persist(Seq(build, anotherBuildForTheSameJob)).futureValue shouldBe UpdateResult(
          nSuccesses = 2,
          nFailures = 0
      )

      repository.collection.find().toFuture.futureValue.length shouldBe 2
      repository.collection.find().toFuture.futureValue should contain theSameElementsAs Seq(build, anotherBuildForTheSameJob)
    }

    "allow to save high number of build objects" in {

      val builds = buildsList(1100).generateOne

      repository
        .persist(builds)
        .futureValue shouldBe UpdateResult(
        nSuccesses = builds.size,
        nFailures = 0
      )

      repository.collection.countDocuments.toFuture.futureValue shouldBe builds.size
    }
  }

  "getForRepository" should {

    "return all builds for the given repository" in {
      val build = builds.generateOne
      val anotherBuildForTheSameJob = build.copy(timestamp = Some(System.currentTimeMillis()), buildNumber = Some(2))
      val buildForDifferentJob = builds.generateOne

      repository.collection.insertOne(build).toFuture.futureValue
      repository.collection.insertOne(anotherBuildForTheSameJob).toFuture.futureValue
      repository.collection.insertOne(buildForDifferentJob).toFuture.futureValue
      repository.collection.countDocuments.toFuture.futureValue shouldBe 3

      val buildsForRepository = repository
        .getForRepository(build.repositoryName.get)
        .futureValue

      buildsForRepository should contain theSameElementsAs Seq(build, anotherBuildForTheSameJob)
    }

    "return no builds if there are not jobs for the given repository" in {

      val builds = repository.getForRepository("unknown repository").futureValue

      builds shouldBe empty
    }
  }

  private def strings(minLength: Int, maxLength: Int): Gen[String] =
    for {
      length <- Gen.chooseNum(minLength, maxLength)
      chars <- Gen.listOfN(length, Gen.alphaNumChar)
    } yield chars.mkString

  private def positiveInts(max: Int): Gen[Int] = Gen.chooseNum(1, max)

  private val builds: Gen[Build] = for {
    repositoryName <- strings(5, 20)
    jobName <- strings(5, 20)
    jobUrl <- strings(5, 40)
    buildNumber <- positiveInts(9999)
    result <- strings(5, 10)
    timestamp <- Gen.const(System.currentTimeMillis())
    duration <- positiveInts(99999)
    buildUrl <- strings(5, 40)
    builtOn <- strings(5, 10)
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

  private val repository = new BuildsRepository(mongoComponent)
  override protected val collectionName: String = repository.collectionName
  override protected val indexes: Seq[IndexModel] = repository.indexes
}