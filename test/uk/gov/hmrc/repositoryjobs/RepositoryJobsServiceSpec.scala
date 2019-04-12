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

import cats.syntax.option._
import org.mockito.Matchers.any
import org.mockito.Mockito._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mockito.MockitoSugar
import org.scalatest.{Matchers, WordSpec}
import uk.gov.hmrc.http.HeaderCarrier

import scala.concurrent.Future

class RepositoryJobsServiceSpec extends WordSpec with Matchers with ScalaFutures with MockitoSugar {

  "Update" should {
    "fetch all builds from ci-open and ci-dev and new build and persist them" in {
      when(connectorCiDev.getBuilds(any(), any()))
        .thenReturn(
          Future.successful(
            JenkinsJobsResponse(List(
              Job(jobName.some, jobUrl.some, List(validBuildResponseDev), serviceGitConfig.some)
            ))))

      when(connectorCiOpen.getBuilds(any(), any()))
        .thenReturn(
          Future.successful(
            JenkinsJobsResponse(List(
              Job(jobName.some, jobUrl.some, List(validBuildResponseOpen), serviceGitConfig.some)
            ))))

      when(connectorCiBuild.getBuilds(any(), any()))
        .thenReturn(
          Future.successful(
            JenkinsJobsResponse(
              List(Job(jobName.some, jobUrl.some, List(validBuildResponseNewBuild), serviceGitConfig.some)))
          ))

      val builds = List(validBuildDev, validBuildOpen, validBuildNewBuild)

      val expectedResult = UpdateResult(nSuccesses = 3, nFailures = 0)

      when(repository.persist(builds))
        .thenReturn(Future.successful(expectedResult))

      repositoryJobsService.update.futureValue shouldBe expectedResult
    }
  }

  implicit val hc: HeaderCarrier = HeaderCarrier()

  val repository: BuildsRepository            = mock[BuildsRepository]
  val connectorCiDev: JenkinsCiDevConnector   = mock[JenkinsCiDevConnector]
  val connectorCiOpen: JenkinsCiOpenConnector = mock[JenkinsCiOpenConnector]
  val connectorCiBuild: JenkinsBuildConnector = mock[JenkinsBuildConnector]

  val repositoryJobsService = new RepositoryJobsService(repository, connectorCiDev, connectorCiOpen, connectorCiBuild)

  val repoName         = "repoName"
  val serviceGitConfig = Scm(List(UserRemoteConfig(repoName.some)).some)
  val jobName          = "jobName"
  val jobUrl           = "jobUrl"

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

  val validBuildResponseNewBuild =
    BuildResponse(
      "validBuildResponseNewBuild".some,
      118869.some,
      "1234".some,
      1234.some,
      "SUCCESS".some,
      1490611944494L.some,
      "buildurlNewBuild".some,
      "builton".some)

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

  val validBuildNewBuild =
    Build(
      repositoryName = repoName.some,
      jobName        = jobName.some,
      jobUrl         = jobUrl.some,
      buildNumber    = validBuildResponseNewBuild.number,
      result         = validBuildResponseNewBuild.result,
      timestamp      = validBuildResponseNewBuild.timestamp,
      duration       = validBuildResponseNewBuild.duration,
      buildUrl       = validBuildResponseNewBuild.url,
      builtOn        = validBuildResponseNewBuild.builtOn
    )
}
