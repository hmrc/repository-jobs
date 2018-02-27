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
import cats.syntax.option._

class RepositoryJobsServiceSpec extends UnitSpec with ScalaFutures with MockitoSugar {

  "Repository jobs service" should {

    "Insert any new builds found in jenkins" in {

      val connector  = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))

      val serviceGitConfig        = Scm(Seq(UserRemoteConfig("service-repo".some)).some)
      val anotherServiceGitConfig = Scm(Seq(UserRemoteConfig("another-service-repo".some)).some)

      val serviceBuilds = Seq(
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-2".some,
          218869.some,
          "124".some,
          124.some,
          "SUCCESS".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some)
      )

      val anotherServiceBuilds = Seq(
        BuildResponse(
          "description-3".some,
          218869.some,
          "223".some,
          223.some,
          "SUCCESS".some,
          1486481417000L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-4".some,
          218869.some,
          "224".some,
          224.some,
          "SUCCESS".some,
          1486135916000L.some,
          "buildurl".some,
          "builton".some)
      )

      when(connector.getBuilds).thenReturn(
        Future.successful(JenkinsJobsResponse(Seq(
          Job("service".some, "jobUrl".some, serviceBuilds.some, serviceGitConfig.some),
          Job("another-service".some, "anotherUrl".some, anotherServiceBuilds.some, anotherServiceGitConfig.some)
        ))))

      when(repository.getAll).thenReturn(Future.successful(Seq[Build]()))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          124.some,
          "SUCCESS".some,
          1486571225000L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository).add(
        Build(
          "another-service-repo".some,
          "another-service".some,
          "anotherUrl".some,
          223.some,
          "SUCCESS".some,
          1486481417000L.some,
          218869.some,
          "buildurl".some,
          "builton".some
        ))
      verify(repository).add(
        Build(
          "another-service-repo".some,
          "another-service".some,
          "anotherUrl".some,
          224.some,
          "SUCCESS".some,
          1486135916000L.some,
          218869.some,
          "buildurl".some,
          "builton".some
        ))
    }

    "Insert only builds with valid results found in jenkins" in {

      val connector  = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))

      val serviceGitConfig        = Scm(Seq(UserRemoteConfig("service-repo".some)).some)
      val anotherServiceGitConfig = Scm(Seq(UserRemoteConfig("another-service-repo".some)).some)

      val serviceBuilds = Seq(
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SOME_RESULT".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "not-to-be-inserted".some,
          218869.some,
          "123".some,
          123.some,
          None,
          1490611944493L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-2".some,
          218869.some,
          "124".some,
          124.some,
          "SOME_OTHER_RESULT".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some)
      )

      when(connector.getBuilds).thenReturn(
        Future.successful(
          JenkinsJobsResponse(Seq(
            Job("service".some, "jobUrl".some, serviceBuilds.some, serviceGitConfig.some)
          ))))

      when(repository.getAll).thenReturn(Future.successful(Seq[Build]()))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          123.some,
          "SOME_RESULT".some,
          1490611944493L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          124.some,
          "SOME_OTHER_RESULT".some,
          1486571225000L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository, times(2)).add(any())

    }

    "Not add existing builds" in {
      val connector  = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))

      val serviceGitConfig = Scm(Seq(UserRemoteConfig("service-repo".some)).some)

      val serviceBuilds = Seq(
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-2".some,
          218869.some,
          "124".some,
          124.some,
          "SUCCESS".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-3".some,
          218869.some,
          "124".some,
          125.some,
          "SUCCESS".some,
          1486481417000L.some,
          "buildurl".some,
          "builton".some)
      )

      when(connector.getBuilds).thenReturn(
        Future.successful(
          JenkinsJobsResponse(Seq(
            Job("service".some, "jobUrl".some, serviceBuilds.some, serviceGitConfig.some)
          ))))

      when(repository.getAll).thenReturn(
        Future.successful(Seq(
          Build(
            "service-repo".some,
            "service".some,
            "jobUrl".some,
            123.some,
            "SUCCESS".some,
            1490611944493L.some,
            218869.some,
            "buildurl".some,
            "builton".some),
          Build(
            "service-repo".some,
            "service".some,
            "jobUrl".some,
            124.some,
            "SUCCESS".some,
            1486571225000L.some,
            218869.some,
            "buildurl".some,
            "builton".some)
        )))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          125.some,
          "SUCCESS".some,
          1486481417000L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository, times(1)).add(any())
    }

    "uniquely identify jobs using the jobname and timestamp" in {

      val connector  = mock[JenkinsConnector]
      val repository = mock[BuildsRepository]

      when(repository.add(any())).thenReturn(Future.successful(true))

      val serviceGitConfig        = Scm(Seq(UserRemoteConfig("service-repo".some)).some)
      val anotherServiceGitConfig = Scm(Seq(UserRemoteConfig("another-service-repo".some)).some)

      val serviceBuilds = Seq(
        BuildResponse(
          "description-1".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some),
        BuildResponse(
          "description-2".some,
          218869.some,
          "123".some,
          123.some,
          "SUCCESS".some,
          1486571225000L.some,
          "buildurl".some,
          "builton".some)
      )

      val anotherServiceBuilds = Seq(
        BuildResponse(
          "description-3".some,
          218869.some,
          "223".some,
          223.some,
          "SUCCESS".some,
          1490611944493L.some,
          "buildurl".some,
          "builton".some))

      when(connector.getBuilds).thenReturn(
        Future.successful(JenkinsJobsResponse(Seq(
          Job("service".some, "jobUrl".some, serviceBuilds.some, serviceGitConfig.some),
          Job("another-service".some, "anotherUrl".some, anotherServiceBuilds.some, anotherServiceGitConfig.some)
        ))))

      when(repository.getAll).thenReturn(
        Future.successful(
          Seq(
            Build(
              "service-repo".some,
              "service".some,
              "jobUrl".some,
              123.some,
              "SUCCESS".some,
              1490611944493L.some,
              218869.some,
              "buildurl".some,
              "builton".some)
          )))

      val service = new RepositoryJobsService(repository, connector)
      await(service.update)

      verify(repository).add(
        Build(
          "service-repo".some,
          "service".some,
          "jobUrl".some,
          123.some,
          "SUCCESS".some,
          1486571225000L.some,
          218869.some,
          "buildurl".some,
          "builton".some))
      verify(repository).add(
        Build(
          "another-service-repo".some,
          "another-service".some,
          "anotherUrl".some,
          223.some,
          "SUCCESS".some,
          1490611944493L.some,
          218869.some,
          "buildurl".some,
          "builton".some
        ))

    }

  }

}
