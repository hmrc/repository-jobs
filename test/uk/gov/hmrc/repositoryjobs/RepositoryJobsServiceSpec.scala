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

import cats.syntax.option._
import org.mockito.Mockito
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen, Matchers, WordSpec}
import play.modules.reactivemongo.ReactiveMongoComponent
import scala.concurrent.Future
import scala.concurrent.duration._
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import scala.concurrent.ExecutionContext.Implicits.global

class RepositoryJobsServiceSpec
    extends WordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with MongoSpecSupport
    with BeforeAndAfterEach
    with GivenWhenThen {

  "Repository jobs service" should {
    "Pick only new valid builds to save" in {
      val repoName = "repoName"
      val serviceGitConfig =
        Scm(List(UserRemoteConfig(repoName.some)).some)

      val validBuildResponse =
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some)

      val invalidBuildResponse =
        BuildResponse(
          "description-2".some,
          218869.some,
          "123".some,
          123.some,
          None,
          1486571225111L.some,
          "buildurl".some,
          "builton".some)

      val existingBuildResponse =
        BuildResponse(
          "description-3".some,
          218869.some,
          "124".some,
          124.some,
          "SUCCESS".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some)

      val jobs =
        List(
          Job(
            name      = "jobName".some,
            url       = "jobUrl".some,
            allBuilds = List(validBuildResponse, invalidBuildResponse, existingBuildResponse),
            scm       = serviceGitConfig.some))

      val existingBuilds =
        List(
          Build(
            repositoryName = "repoName".some,
            jobName        = "jobName".some,
            jobUrl         = "jobUrl".some,
            buildNumber    = existingBuildResponse.number,
            result         = existingBuildResponse.result,
            timestamp      = existingBuildResponse.timestamp,
            duration       = existingBuildResponse.duration,
            buildUrl       = existingBuildResponse.url,
            builtOn        = existingBuildResponse.builtOn
          ))

      repositoryJobsService.getBuilds(jobs, existingBuilds) should contain theSameElementsAs
        List(
          Build(
            repositoryName = "repoName".some,
            jobName        = "jobName".some,
            jobUrl         = "jobUrl".some,
            buildNumber    = validBuildResponse.number,
            result         = validBuildResponse.result,
            timestamp      = validBuildResponse.timestamp,
            duration       = validBuildResponse.duration,
            buildUrl       = validBuildResponse.url,
            builtOn        = validBuildResponse.builtOn
          )
        )
    }

    "Insert valid new builds and return the number of mongo successes and failures" in {
      val repoName = "repoName"

      val serviceGitConfig =
        Scm(List(UserRemoteConfig(repoName.some)).some)

      Given("Jenkins returns a mix of valid, invalid and existing builds")
      val validBuildResponse =
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some)

      val invalidBuildResponse =
        BuildResponse(
          "description-2".some,
          218869.some,
          "123".some,
          123.some,
          None,
          1486571225111L.some,
          "buildurl".some,
          "builton".some)

      val existingBuildResponse =
        BuildResponse(
          "description-3".some,
          218869.some,
          "124".some,
          124.some,
          "SUCCESS".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some)

      val serviceBuilds =
        List(validBuildResponse, invalidBuildResponse, existingBuildResponse)

      val jobName = "jobName"
      val jobUrl  = "jobUrl"

      Mockito
        .when(connector.getBuilds)
        .thenReturn(
          Future.successful(
            JenkinsJobsResponse(List(
              Job(jobName.some, jobUrl.some, serviceBuilds, serviceGitConfig.some)
            ))))

      val existingBuild =
        Build(
          repositoryName = repoName.some,
          jobName        = jobName.some,
          jobUrl         = jobUrl.some,
          buildNumber    = existingBuildResponse.number,
          result         = existingBuildResponse.result,
          timestamp      = existingBuildResponse.timestamp,
          duration       = existingBuildResponse.duration,
          buildUrl       = existingBuildResponse.url,
          builtOn        = existingBuildResponse.builtOn
        )

      And("mongo contains the existing builds")
      repository.bulkAdd(List(existingBuild)).futureValue

      When("we update the builds")
      val updateResult = repositoryJobsService.update.futureValue

      Then("only the new builds are saved")
      val validBuild =
        Build(
          repositoryName = repoName.some,
          jobName        = jobName.some,
          jobUrl         = jobUrl.some,
          buildNumber    = validBuildResponse.number,
          result         = validBuildResponse.result,
          timestamp      = validBuildResponse.timestamp,
          duration       = validBuildResponse.duration,
          buildUrl       = validBuildResponse.url,
          builtOn        = validBuildResponse.builtOn
        )

      repository.getAll.futureValue should contain theSameElementsAs
        List(existingBuild, validBuild)

      And("the result should return the number of successes and failures")
      updateResult.nSuccesses shouldBe 1
      updateResult.nFailures  shouldBe 0
    }
  }

  override def beforeEach(): Unit =
    repository.drop.futureValue

  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds)

  val repository = new BuildsRepository(new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  })

  val connector: JenkinsConnector = mock[JenkinsConnector]

  val repositoryJobsService = new RepositoryJobsService(repository, connector)
}
