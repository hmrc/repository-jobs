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

import com.github.tomakehurst.wiremock.http.RequestMethod._
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.OneAppPerSuite
import cats.syntax.option._
import org.scalatest.mock.MockitoSugar
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig

class JenkinsConnectorSpec
    extends WordSpec
    with Matchers
    with WireMockEndpoints
    with ScalaFutures
    with OneAppPerSuite
    with OptionValues
    with MockitoSugar {
  implicit val defaultPatienceConfig = new PatienceConfig(Span(200, Millis), Span(15, Millis))

  "Getting all jobs from jenkins" should {

    "Deserialise the response upon a successful request" in {

      val connector = new JenkinsConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override def jenkinsBaseUrl: String = endpointMockUrl
      }

      serviceEndpoint(GET, connector.buildsUrl, willRespondWith = (200, Some(JsonData.jenkinsJobsResponse)))

      val result = connector.getBuilds

      result.futureValue.jobs.length shouldBe 1

      val job = result.futureValue.jobs.head
      job.name                                       shouldBe "address-lookup".some
      job.url                                        shouldBe "https://ci/job/address-lookup/".some
      job.scm.value.userRemoteConfigs.value.head.url shouldBe "git@github:HMRC/address-lookup.git".some

      job.allBuilds.value.length shouldBe 1
      job.allBuilds.value.head shouldBe BuildResponse(
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

      val connector = new JenkinsConnector(app.injector.instanceOf[HttpClient], mock[RepositoryJobsConfig]) {
        override def jenkinsBaseUrl: String = endpointMockUrl
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

}
