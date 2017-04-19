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
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatestplus.play.OneAppPerSuite
import uk.gov.hmrc.play.test.UnitSpec

class JenkinsConnectorSpec extends UnitSpec with WireMockEndpoints with ScalaFutures with OneAppPerSuite {
  implicit val defaultPatienceConfig = new PatienceConfig(Span(200, Millis), Span(15, Millis))

  "Getting all jobs from jenkins" should {

    "Deserialise the response upon a successful request" in {

      val connector = new JenkinsConnector {
        override val http = WSHttp
        override def jenkinsBaseUrl: String = endpointMockUrl
      }

      serviceEndpoint(
        GET,
        connector.buildsUrl,
        willRespondWith = (200, Some(JsonData.jenkinsJobsResponse)))

      val result = connector.getBuilds

      result.jobs.length shouldBe 1

      val job = result.jobs.head
      job.name shouldBe "address-lookup"
      job.url shouldBe "https://ci/job/address-lookup/"
      job.scm.userRemoteConfigs.head.url shouldBe "git@github:HMRC/address-lookup.git"

      job.allBuilds.length shouldBe 1
      job.allBuilds.head shouldBe BuildResponse("1ccc1869ee8fcd8ff28701b5804c880106b38771",218869,"2017-02-08_16-32-42",126,"SUCCESS",1.486571562E12,"https://ci/job/address-lookup/126/","ci-slave-9")

    }

  }

}
