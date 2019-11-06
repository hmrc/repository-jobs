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
import com.github.tomakehurst.wiremock.http.RequestMethod._
import com.google.common.io.BaseEncoding
import org.mockito.ArgumentCaptor
import org.mockito.Matchers.any
import org.mockito.Mockito.when
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatestplus.mockito.MockitoSugar
import org.scalatestplus.play.guice.GuiceOneAppPerSuite
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class JenkinsConnectorSpec
    extends WordSpec
    with Matchers
    with WireMockEndpoints
    with ScalaFutures
    with GuiceOneAppPerSuite
    with OptionValues
    with MockitoSugar {
  implicit val defaultPatienceConfig: PatienceConfig = PatienceConfig(Span(200, Millis), Span(15, Millis))
  implicit val hc: HeaderCarrier                     = HeaderCarrier()

  "Getting all jobs from jenkins" should {
    "Deserialise the response upon a successful request" in {
      val connector = new JenkinsCiDevConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override val host: String = endpointMockUrl
      }

      serviceEndpoint(GET, connector.buildsUrl, willRespondWith = (200, Some(JsonData.jenkinsJobsResponse)))

      val result = connector.getBuilds

      result.futureValue.jobs.length shouldBe 1

      val job = result.futureValue.jobs.head
      job.name                                       shouldBe "address-lookup".some
      job.url                                        shouldBe "https://ci/job/address-lookup/".some
      job.scm.value.userRemoteConfigs.value.head.url shouldBe "git@github:HMRC/address-lookup.git".some

      job.allBuilds.length shouldBe 1
      job.allBuilds.head shouldBe BuildResponse(
        "1ccc1869ee8fcd8ff28701b5804c880106b38771".some,
        218869.some,
        "2017-02-08_16-32-42".some,
        126.some,
        "SUCCESS".some,
        1486571562000L.some,
        "https://ci/job/address-lookup/126/".some,
        "ci-slave-9".some
      )

    }

    "handle control characters in the response body" in {
      val connector = new JenkinsCiDevConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override val host: String = endpointMockUrl
      }

      serviceEndpoint(
        GET,
        connector.buildsUrl,
        willRespondWith = (200, Some(JsonData.jenkinsJobsResponseWithControlCharacters)))

      val result = connector.getBuilds

      result.futureValue.jobs.length shouldBe 1

      val job = result.futureValue.jobs.head
      job.name shouldBe "address-lookup".some

    }

  }

  "Getting all jobs from jenkins new build" should {
    "Deserialise the response containing a nested collection of folders and jobs upon a successful request" in {
      val connector = new JenkinsBuildConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override val host: String = endpointMockUrl
      }

      serviceEndpoint(GET, connector.buildsUrl, willRespondWith = (200, Some(JsonData.jenkinsJobsNewBuildResponse)))

      val result = connector.getBuilds

      result.futureValue.jobs.length shouldBe 1

      val job = result.futureValue.jobs.head

      job.name                                       shouldBe "platops-example-api-tests".some
      job.allBuilds                                  shouldBe empty
      job.scm.value.userRemoteConfigs.value.head.url shouldBe "https://github.com/hmrc/platops-example-api-tests.git".some
    }

    "handle control characters in the response body" in {
      val connector = new JenkinsBuildConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override val host: String = endpointMockUrl
      }

      serviceEndpoint(
        GET,
        connector.buildsUrl,
        willRespondWith = (200, Some(JsonData.jenkinsJobsNewBuildResponseWithControlCharacters)))

      val result = connector.getBuilds

      result.futureValue.jobs.length shouldBe 1

      val job = result.futureValue.jobs.head
      job.name shouldBe "platops-example-api-tests".some
    }
  }

  "The jenkins connector" should {
    "use basic auth if an authorization header is provided" in {
      val httpClient                 = mock[HttpClient]
      val expectedHttpResponse       = HttpResponse(200, responseString = Some(JsonData.jenkinsJobsNewBuildResponse))
      implicit val hc: HeaderCarrier = HeaderCarrier(authorization = None)

      val argumentCaptor = ArgumentCaptor.forClass(classOf[HeaderCarrier])

      when(
        httpClient
          .GET[HttpResponse](any())(any(), argumentCaptor.capture(), any()))
        .thenReturn(Future(expectedHttpResponse))

      val repositoryJobsConfig = mock[RepositoryJobsConfig]

      val connector = new JenkinsBuildConnector(httpClient, repositoryJobsConfig)

      val _ = connector.getBuilds.futureValue

      val encodedCredentials = BaseEncoding
        .base64()
        .encode(s"${repositoryJobsConfig.username}:${repositoryJobsConfig.password}".getBytes("UTF-8"))

      argumentCaptor.getValue.authorization shouldBe Some(Authorization(s"Basic $encodedCredentials"))
    }
  }

}
