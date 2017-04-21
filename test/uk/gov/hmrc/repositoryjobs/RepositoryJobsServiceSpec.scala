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

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.mock.MockitoSugar
import org.mockito.Mockito._
import org.mockito.Matchers._
import uk.gov.hmrc.play.test.UnitSpec

import scala.concurrent.Future

class RepositoryJobsServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  "Repository jobs service" should {

    "Insert any new builds found in jenkins" in {

      val connector = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))

      val serviceGitConfig = Scm(Seq(UserRemoteConfig("service-repo")))
      val anotherServiceGitConfig = Scm(Seq(UserRemoteConfig("another-service-repo")))

      val serviceBuilds = Seq(
        BuildResponse("description-1", 218869, "123", 123, "SUCCESS", 1490611944493L, "buildurl", "builton"),
        BuildResponse("description-2", 218869, "124", 124, "SUCCESS", 1486571225000L, "buildurl", "builton"))

      val anotherServiceBuilds = Seq(
        BuildResponse("description-3", 218869, "223", 223, "SUCCESS", 1486481417000L, "buildurl", "builton"),
        BuildResponse("description-4", 218869, "224", 224, "SUCCESS", 1486135916000L, "buildurl", "builton"))

      when(connector.getBuilds).thenReturn(Future.successful(JenkinsJobsResponse(Seq(
        Job("service", "jobUrl", serviceBuilds, serviceGitConfig),
        Job("another-service", "anotherUrl", anotherServiceBuilds, anotherServiceGitConfig)
      ))))

      when(repository.getAll).thenReturn(Future.successful(Seq[Build]()))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(Build("service-repo", "service", "jobUrl", 123, "SUCCESS", 1490611944493L, 218869, "buildurl", "builton"))
      verify(repository).add(Build("service-repo", "service", "jobUrl", 124, "SUCCESS", 1486571225000L, 218869, "buildurl", "builton"))
      verify(repository).add(Build("another-service-repo", "another-service", "anotherUrl", 223, "SUCCESS", 1486481417000L, 218869, "buildurl", "builton"))
      verify(repository).add(Build("another-service-repo", "another-service", "anotherUrl", 224, "SUCCESS", 1486135916000L, 218869, "buildurl", "builton"))
    }

    "Not add existing builds" in {
      val connector = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))


      val serviceGitConfig = Scm(Seq(UserRemoteConfig("service-repo")))

      val serviceBuilds = Seq(
        BuildResponse("description-1", 218869, "123", 123, "SUCCESS", 1490611944493L, "buildurl", "builton"),
        BuildResponse("description-2", 218869, "124", 124, "SUCCESS", 1486571225000L, "buildurl", "builton"),
        BuildResponse("description-3", 218869, "124", 125, "SUCCESS", 1486481417000L, "buildurl", "builton"))

      when(connector.getBuilds).thenReturn(Future.successful(JenkinsJobsResponse(Seq(
        Job("service", "jobUrl", serviceBuilds, serviceGitConfig)
      ))))

      when(repository.getAll).thenReturn(Future.successful(Seq(
        Build("service-repo", "service", "jobUrl", 123, "SUCCESS", 1490611944493L, 218869, "buildurl", "builton"),
        Build("service-repo", "service", "jobUrl", 124, "SUCCESS", 1486571225000L, 218869, "buildurl", "builton")
      )))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(Build("service-repo", "service", "jobUrl", 125, "SUCCESS", 1486481417000L, 218869, "buildurl", "builton"))
      verify(repository, times(1)).add(any())
    }

    "uniquely identify jobs using the jobname and timestamp" in {

      val connector = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))


      val serviceGitConfig = Scm(Seq(UserRemoteConfig("service-repo")))
      val anotherServiceGitConfig = Scm(Seq(UserRemoteConfig("another-service-repo")))

      val serviceBuilds = Seq(
        BuildResponse("description-1", 218869, "123", 123, "SUCCESS", 1490611944493L, "buildurl", "builton"),
        BuildResponse("description-2", 218869, "123", 123, "SUCCESS", 1486571225000L, "buildurl", "builton"))

      val anotherServiceBuilds = Seq(
        BuildResponse("description-3", 218869, "223", 223, "SUCCESS", 1490611944493L, "buildurl", "builton"))

      when(connector.getBuilds).thenReturn(Future.successful(JenkinsJobsResponse(Seq(
        Job("service", "jobUrl", serviceBuilds, serviceGitConfig),
        Job("another-service", "anotherUrl", anotherServiceBuilds, anotherServiceGitConfig)
      ))))

      when(repository.getAll).thenReturn(Future.successful(Seq(
        Build("service-repo", "service", "jobUrl", 123, "SUCCESS", 1490611944493L, 218869, "buildurl", "builton")
      )))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(Build("service-repo", "service", "jobUrl", 123, "SUCCESS", 1486571225000L, 218869, "buildurl", "builton"))
      verify(repository).add(Build("another-service-repo", "another-service", "anotherUrl", 223, "SUCCESS", 1490611944493L, 218869, "buildurl", "builton"))

    }

  }

}
