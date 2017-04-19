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

import play.api.Logger
import play.api.libs.json.Json
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future

case class BuildResponse(description: String, duration: Int, id: String, number: Int, result: String,
                         timestamp: Double, url: String, builtOn: String)

case class UserRemoteConfig(url: String)
case class Scm(userRemoteConfigs: Seq[UserRemoteConfig])
case class Job(name: String, url: String, allBuilds: Seq[BuildResponse], scm: Scm)
case class JenkinsJobsResponse(jobs: Seq[Job])

trait JenkinsConnector {

  def http: HttpGet

  def jenkinsBaseUrl: String

  implicit val buildReads = Json.reads[BuildResponse]
  implicit val userRemoteConfigReads = Json.reads[UserRemoteConfig]
  implicit val scmReads = Json.reads[Scm]
  implicit val jobReads = Json.reads[Job]
  implicit val jenkinsReads = Json.reads[JenkinsJobsResponse]

  val buildsUrl = "/api/json?tree=" + java.net.URLEncoder.encode("jobs[name,url,allBuilds[id,description,duration,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]", "UTF-8")

  def getBuilds: Future[JenkinsJobsResponse] = {
    implicit val hc = new HeaderCarrier()

    val url = jenkinsBaseUrl + buildsUrl
    http.GET[JenkinsJobsResponse](url).recover {
      case ex =>
        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
        throw ex
    }
  }

}
