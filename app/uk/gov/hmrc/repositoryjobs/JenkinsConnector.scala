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

import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json}
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig

case class BuildResponse(
  description: Option[String],
  duration: Option[Int],
  id: Option[String],
  number: Option[Int],
  result: Option[String],
  timestamp: Option[Long],
  url: Option[String],
  builtOn: Option[String])

case class UserRemoteConfig(url: Option[String])
case class Scm(userRemoteConfigs: Option[Seq[UserRemoteConfig]])
case class Job(name: Option[String], url: Option[String], allBuilds: Seq[BuildResponse], scm: Option[Scm])
case class JenkinsJobsResponse(jobs: Seq[Job])

@Singleton
class JenkinsConnector @Inject()(http: HttpClient, repositoryJobsConfig: RepositoryJobsConfig) {

  def jenkinsBaseUrl: String = repositoryJobsConfig.jobsApiBase

  implicit val buildReads            = Json.format[BuildResponse]
  implicit val userRemoteConfigReads = Json.format[UserRemoteConfig]
  implicit val scmReads              = Json.format[Scm]
  implicit val jobReads              = Json.format[Job]
  implicit val jenkinsReads          = Json.format[JenkinsJobsResponse]

  val buildsUrl = "/api/json?tree=" + java.net.URLEncoder.encode(
    "jobs[name,url,allBuilds[id,description,duration,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]",
    "UTF-8")

  def getBuilds: Future[JenkinsJobsResponse] = {
    implicit val hc = new HeaderCarrier()

    val url = jenkinsBaseUrl + buildsUrl

    val result = http
      .GET[HttpResponse](url)
      .recoverWith {
        case NonFatal(ex) =>
          Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
          Future.failed(ex)
      }
      .map(
        httpResponse =>
          Try(
            Json
              .parse(httpResponse.body.replaceAll("\\p{Cntrl}", ""))
              .validate[JenkinsJobsResponse]) match {
            case Success(jsResult) => jsResult
            case Failure(t)        => JsError(t.getMessage)
        })

    result.map {
      case JsSuccess(jenkinsResponse, _) =>
        jenkinsResponse
      case JsError(e) =>
        throw new RuntimeException(s"${e.toString()}")
    }
  }

}
