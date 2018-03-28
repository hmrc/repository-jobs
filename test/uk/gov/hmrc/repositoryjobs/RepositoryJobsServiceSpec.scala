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
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterEach, GivenWhenThen, Matchers, WordSpec}
import play.modules.reactivemongo.ReactiveMongoComponent
import uk.gov.hmrc.mongo.{MongoConnector, MongoSpecSupport}
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RepositoryJobsServiceSpec
    extends WordSpec
    with Matchers
    with ScalaFutures
    with MockitoSugar
    with MongoSpecSupport
    with BeforeAndAfterEach
    with GivenWhenThen
    with PropertyChecks
    with WireMockEndpoints {

  "Repository jobs service" should {
    "return an empty list when there are no new builds" in {
      forAll(genJobsWithNoNewBuilds) {
        case (jobs, builds) =>
          repositoryJobsService.getBuilds(jobs, builds).size shouldBe 0
      }
    }

    "return a list of only new builds when new builds exist" in {
      forAll(genJobsWithNewBuilds) {
        case (jobs, existingBuilds, numberOfNewBuilds) =>
          repositoryJobsService.getBuilds(jobs, existingBuilds).size shouldBe numberOfNewBuilds
      }
    }

    "only save new valid builds" in {
      repositoryJobsService.getBuilds(jobs, List(existingBuild)) should contain theSameElementsAs
        List(validBuildDev)
    }

    "insert new builds" when {

      "ci-dev contains records and ci-open doesn't contain records" in {

        Mockito
          .when(connectorCiOpen.getBuilds)
          .thenReturn(
            Future.successful(
              JenkinsJobsResponse(List.empty)))

        And("mongo contains the existing builds")
        repository.bulkAdd(List(existingBuild)).futureValue

        When("we update the builds")
        val updateResult = repositoryJobsService.update.futureValue

        Then("only the new builds are saved")

        repository.getAll.futureValue should contain theSameElementsAs
          List(existingBuild, validBuildDev)

        And("the result should return the number of successes and failures")
        updateResult.nSuccesses shouldBe 1
        updateResult.nFailures  shouldBe 0
      }

      "ci-open contains records and ci-dev doesn't contain records" in {

        Mockito
          .when(connectorCiDev.getBuilds)
          .thenReturn(
            Future.successful(
              JenkinsJobsResponse(List.empty)))

        And("mongo contains the existing builds")
        repository.bulkAdd(List(existingBuild)).futureValue

        When("we update the builds")
        val updateResult = repositoryJobsService.update.futureValue

        Then("only the new builds are saved")

        repository.getAll.futureValue should contain theSameElementsAs
          List(existingBuild, validBuildOpen)

        And("the result should return the number of successes and failures")
        updateResult.nSuccesses shouldBe 1
        updateResult.nFailures  shouldBe 0
      }

      "ci-dev and ci-open both contain records" in {

        And("mongo contains the existing builds")
        repository.bulkAdd(List(existingBuild)).futureValue

        When("we update the builds")
        val updateResult = repositoryJobsService.update.futureValue

        Then("only the new builds are saved")

        repository.getAll.futureValue should contain theSameElementsAs
          List(existingBuild, validBuildDev, validBuildOpen)

        And("the result should return the number of successes and failures")
        updateResult.nSuccesses shouldBe 2
        updateResult.nFailures  shouldBe 0
      }

    }

  }

  val repoName = "repoName"

  val serviceGitConfig =
    Scm(List(UserRemoteConfig(repoName.some)).some)

  override def beforeEach(): Unit = {
    repository.drop.futureValue

    Mockito
      .when(config.ciDevUrl)
      .thenReturn(endpointMockUrl)

    Mockito
      .when(connectorCiDev.getBuilds)
      .thenReturn(
        Future.successful(
          JenkinsJobsResponse(List(
            Job(jobName.some, jobUrl.some, serviceBuildsDev, serviceGitConfig.some)
          ))))

    Mockito
      .when(connectorCiOpen.getBuilds)
      .thenReturn(
        Future.successful(
          JenkinsJobsResponse(List(
            Job(jobName.some, jobUrl.some, serviceBuildsOpen, serviceGitConfig.some)
          ))))
  }


  override implicit val patienceConfig: PatienceConfig =
    PatienceConfig(timeout = 5.seconds)

  val repository = new BuildsRepository(new ReactiveMongoComponent {
    override def mongoConnector: MongoConnector = mongoConnectorForTest
  })

  val connectorCiDev: JenkinsCiDevConnector = mock[JenkinsCiDevConnector]
  val connectorCiOpen: JenkinsCiOpenConnector = mock[JenkinsCiOpenConnector]
  val config: RepositoryJobsConfig = mock[RepositoryJobsConfig]

  val repositoryJobsService = new RepositoryJobsService(repository, connectorCiDev, connectorCiOpen, config)

  case class BuildId(jobName: String, timestamp: Long)

  val genUniqueBuildIds: Gen[Set[BuildId]] =
    Gen
      .listOf(for {
        jobName   <- Gen.alphaStr
        timestamp <- Arbitrary.arbLong.arbitrary
      } yield BuildId(jobName, timestamp))
      .map(_.toSet)

  def createBuildResponse(buildId: BuildId): BuildResponse =
    BuildResponse(
      description = None,
      duration    = None,
      id          = None,
      number      = None,
      result      = Some("success"),
      timestamp   = Some(buildId.timestamp),
      url         = None,
      builtOn     = None)

  def createJobs(buildIds: Set[BuildId]): Seq[Job] =
    buildIds
      .groupBy(_.jobName)
      .map {
        case (jobName, buildIdsForJob) =>
          Job(Some(jobName), None, buildIdsForJob.map(createBuildResponse).toSeq, None)
      }
      .toSeq

  def createBuilds(buildIds: Set[BuildId]): Seq[Build] =
    buildIds.map {
      case BuildId(jobName, timestamp) =>
        Build(
          repositoryName = None,
          jobName        = Some(jobName),
          jobUrl         = None,
          buildNumber    = None,
          result         = None,
          timestamp      = Some(timestamp),
          duration       = None,
          buildUrl       = None,
          builtOn        = None)
    }.toSeq

  val genJobsWithNoNewBuilds: Gen[(Seq[Job], Seq[Build])] =
    genUniqueBuildIds.map(jobIds => (createJobs(jobIds), createBuilds(jobIds)))

  val genJobsWithNewBuilds: Gen[(Seq[Job], Seq[Build], Int)] =
    for {
      (jobs, existingBuilds) <- genJobsWithNoNewBuilds
      numberOfNewBuilds      <- Gen.choose(0, existingBuilds.size)

      existingBuildsAfterDropping = existingBuilds.drop(numberOfNewBuilds)
    } yield (jobs, existingBuildsAfterDropping, numberOfNewBuilds)

  val jobName = "jobName"
  val jobUrl  = "jobUrl"

  val validBuildResponseDev =
    BuildResponse(
      "validBuildResponseDev".some,
      218869.some,
      "123".some,
      123.some,
      "SUCCESS".some,
      1490611944493L.some,
      "buildurl".some,
      "builton".some)

  val validBuildResponseOpen =
    BuildResponse(
      "validBuildResponseOpen".some,
      118869.some,
      "1234".some,
      1234.some,
      "SUCCESS".some,
      1490611944494L.some,
      "buildurlOpen".some,
      "builton".some)

  val invalidBuildResponse =
    BuildResponse(
      "invalidBuildResponse".some,
      218869.some,
      "123".some,
      123.some,
      None,
      1486571225111L.some,
      "buildurl".some,
      "builton".some)

  val existingBuildResponse =
    BuildResponse(
      "existingBuildResponse".some,
      218869.some,
      "124".some,
      124.some,
      "SUCCESS".some,
      1486571225000L.some,
      "buildurl".some,
      "builton".some)

  val serviceBuildsDev =
    List(validBuildResponseDev, invalidBuildResponse, existingBuildResponse)

  val serviceBuildsOpen =
    List(validBuildResponseOpen, invalidBuildResponse, existingBuildResponse)

  val jobs =
    List(
      Job(
        name      = jobName.some,
        url       = jobUrl.some,
        allBuilds = List(validBuildResponseDev, invalidBuildResponse, existingBuildResponse),
        scm       = serviceGitConfig.some))

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

  val validBuildDev =
    Build(
      repositoryName = repoName.some,
      jobName        = jobName.some,
      jobUrl         = jobUrl.some,
      buildNumber    = validBuildResponseDev.number,
      result         = validBuildResponseDev.result,
      timestamp      = validBuildResponseDev.timestamp,
      duration       = validBuildResponseDev.duration,
      buildUrl       = validBuildResponseDev.url,
      builtOn        = validBuildResponseDev.builtOn
    )
  val validBuildOpen =
    Build(
      repositoryName = repoName.some,
      jobName        = jobName.some,
      jobUrl         = jobUrl.some,
      buildNumber    = validBuildResponseOpen.number,
      result         = validBuildResponseOpen.result,
      timestamp      = validBuildResponseOpen.timestamp,
      duration       = validBuildResponseOpen.duration,
      buildUrl       = validBuildResponseOpen.url,
      builtOn        = validBuildResponseOpen.builtOn
    )
}