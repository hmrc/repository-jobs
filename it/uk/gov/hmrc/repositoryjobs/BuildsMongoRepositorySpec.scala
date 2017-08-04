/*
 * Copyright 2017 HM Revenue & Customs
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

import org.scalatest.BeforeAndAfterEach
import play.api.libs.json.Json
import uk.gov.hmrc.mongo.MongoSpecSupport
import uk.gov.hmrc.play.test.UnitSpec
import scala.concurrent.ExecutionContext.Implicits.global

import reactivemongo.json._

class BuildsMongoRepositorySpec extends UnitSpec with MongoSpecSupport with BeforeAndAfterEach  {
  val buildsRepository = new BuildsMongoRepository(mongo)

  override def beforeEach() {
    await(buildsRepository.drop)
  }

  "getForRepository" should {

    "return all the builds for the given repository" in {

      await(buildsRepository.collection.insert(Json.obj(
        "repositoryName" -> "cato-acceptance-tests",
        "jobName" -> "cato-acceptance-tests",
        "jobUrl" -> "http://ci/job/cato-acceptance-tests/",
        "buildNumber" -> 123,
        "result" -> "SUCCESS",
        "timestamp" -> 1.486571562E12,
        "duration" -> 218869,
        "buildUrl" -> "https://ci/job/cato-acceptance-tests/123/",
        "builtOn" -> "ci-slave-9"
      )))

      await(buildsRepository.collection.insert(Json.obj(
        "repositoryName" -> "iht-acceptance-tests",
        "jobName" -> "iht-acceptance-tests",
        "jobUrl" -> "https://ci/job/iht/",
        "buildNumber" -> 234,
        "result" -> "SUCCESS",
        "timestamp" -> 1.486571562E12,
        "duration" -> 218869,
        "buildUrl" -> "https://ci/job/iht/234/",
        "builtOn" -> "ci-slave-9"
      )))

      val builds: Seq[Build] = await(buildsRepository.getForRepository("cato-acceptance-tests"))

      builds.size shouldBe 1
      builds.head.repositoryName.get shouldBe "cato-acceptance-tests"
      builds.head.jobName.get shouldBe "cato-acceptance-tests"
      builds.head.jobUrl.get shouldBe "http://ci/job/cato-acceptance-tests/"
      builds.head.buildNumber.get shouldBe 123
      builds.head.result.get shouldBe "SUCCESS"
      builds.head.timestamp.get shouldBe 1.486571562E12
      builds.head.duration.get shouldBe 218869
      builds.head.buildUrl.get shouldBe "https://ci/job/cato-acceptance-tests/123/"
      builds.head.builtOn.get shouldBe "ci-slave-9"

    }
  }

  "getAll" should {

    "return a map of repos to builds" in {

      await(buildsRepository.collection.insert(Json.obj(
        "repositoryName" -> "cato-acceptance-tests",
        "jobName" -> "cato-acceptance-tests",
        "jobUrl" -> "http://ci/job/cato-acceptance-tests/",
        "buildNumber" -> 123,
        "result" -> "SUCCESS",
        "timestamp" -> 1.486571562E12,
        "duration" -> 218869,
        "buildUrl" -> "https://ci/job/cato-acceptance-tests/123/",
        "builtOn" -> "ci-slave-9"
      )))

      await(buildsRepository.collection.insert(Json.obj(
        "repositoryName" -> "iht-acceptance-tests",
        "jobName" -> "iht-acceptance-tests",
        "jobUrl" -> "https://ci/job/iht/",
        "buildNumber" -> 234,
        "result" -> "SUCCESS",
        "timestamp" -> 1.486571562E12,
        "duration" -> 218869,
        "buildUrl" -> "https://ci/job/iht/234/",
        "builtOn" -> "ci-slave-9"
      )))

      val builds = await(buildsRepository.getAllByRepo)

      val catoBuilds = builds("cato-acceptance-tests")
      catoBuilds.size shouldBe 1
      catoBuilds.head.repositoryName.get shouldBe "cato-acceptance-tests"
      catoBuilds.head.jobName.get shouldBe "cato-acceptance-tests"
      catoBuilds.head.jobUrl.get shouldBe "http://ci/job/cato-acceptance-tests/"
      catoBuilds.head.buildNumber.get shouldBe 123
      catoBuilds.head.result.get shouldBe "SUCCESS"
      catoBuilds.head.timestamp.get shouldBe 1.486571562E12
      catoBuilds.head.duration.get shouldBe 218869
      catoBuilds.head.buildUrl.get shouldBe "https://ci/job/cato-acceptance-tests/123/"
      catoBuilds.head.builtOn.get shouldBe "ci-slave-9"

      val ihtBuilds = builds("iht-acceptance-tests")
      ihtBuilds.size shouldBe 1
      ihtBuilds.head.repositoryName.get shouldBe "iht-acceptance-tests"
      ihtBuilds.head.jobName.get shouldBe "iht-acceptance-tests"
      ihtBuilds.head.jobUrl.get shouldBe "https://ci/job/iht/"
      ihtBuilds.head.buildNumber.get shouldBe 234
      ihtBuilds.head.result.get shouldBe "SUCCESS"
      ihtBuilds.head.timestamp.get shouldBe 1.486571562E12
      ihtBuilds.head.duration.get shouldBe 218869
      ihtBuilds.head.buildUrl.get shouldBe "https://ci/job/iht/234/"
      ihtBuilds.head.builtOn.get shouldBe "ci-slave-9"
    }

  }

}
