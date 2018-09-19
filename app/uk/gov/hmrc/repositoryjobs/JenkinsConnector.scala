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

import com.google.common.io.BaseEncoding
import javax.inject.{Inject, Singleton}
import play.api.Logger
import play.api.libs.json.{JsError, JsSuccess, Json, _}
import uk.gov.hmrc.http.logging.Authorization
import uk.gov.hmrc.http.{HeaderCarrier, HttpResponse}
import uk.gov.hmrc.play.bootstrap.http.HttpClient
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.repositoryjobs.JenkinsBuildJobsResponse.flattenJobs
import uk.gov.hmrc.repositoryjobs.JenkinsConnector.apiTree
import uk.gov.hmrc.repositoryjobs.JobTree.jobTreeReads
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig
import scala.concurrent.Future
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

trait JenkinsConnector {
  type JenkinsResponseType

  val host: String
  val http: HttpClient
  val apiTreeSelector: String
  val authorizationHeader: Option[Authorization]

  lazy val buildsUrl: String = "/api/json?tree=" + java.net.URLEncoder.encode(apiTreeSelector, "UTF-8")

  implicit val jenkinsResponseReads: Reads[JenkinsResponseType]

  def convertJenkinsResponse(jenkinsResponse: JenkinsResponseType): JenkinsJobsResponse

  def getBuilds(implicit hc: HeaderCarrier): Future[JenkinsJobsResponse] = {
    val url = host + buildsUrl

    val result = http
      .GET[HttpResponse](url)(implicitly, hc.copy(authorization = authorizationHeader), implicitly)
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
              .validate[JenkinsResponseType]) match {
            case Success(jsResult) => jsResult
            case Failure(t)        => JsError(t.getMessage)
        })

    result.map {
      case JsSuccess(jenkinsResponse, _) => convertJenkinsResponse(jenkinsResponse)
      case JsError(e)                    => throw new RuntimeException(s"${e.toString()}")
    }
  }
}
object JenkinsConnector {
  def apiTree(depth: Int = 1): String = {
    require(depth > 0)
    depth match {
      case 1 =>
        "jobs[name,allBuilds[description,duration,id,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]"
      case n =>
        s"jobs[name,${apiTree(n - 1)},allBuilds[description,duration,id,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]"
    }
  }
}

@Singleton
class JenkinsCiDevConnector @Inject()(httpClient: HttpClient, repositoryJobsConfig: RepositoryJobsConfig)
    extends JenkinsConnector {
  override val host: String            = repositoryJobsConfig.ciDevUrl
  override val http: HttpClient        = httpClient
  override val apiTreeSelector: String = apiTree(1)
  override type JenkinsResponseType = JenkinsJobsResponse
  override implicit val jenkinsResponseReads: Reads[JenkinsResponseType]                         = JenkinsJobsResponse.jenkinsJobsResponseReads
  override def convertJenkinsResponse(jenkinsResponse: JenkinsResponseType): JenkinsJobsResponse = jenkinsResponse
  override val authorizationHeader: Option[Authorization]                                        = None
}

@Singleton
class JenkinsCiOpenConnector @Inject()(httpClient: HttpClient, repositoryJobsConfig: RepositoryJobsConfig)
    extends JenkinsConnector {
  override val host: String            = repositoryJobsConfig.ciOpenUrl
  override val http: HttpClient        = httpClient
  override val apiTreeSelector: String = apiTree(1)
  override type JenkinsResponseType = JenkinsJobsResponse
  override implicit val jenkinsResponseReads: Reads[JenkinsResponseType] =
    JenkinsJobsResponse.jenkinsJobsResponseReads
  override def convertJenkinsResponse(jenkinsResponse: JenkinsResponseType): JenkinsJobsResponse = jenkinsResponse
  override val authorizationHeader: Option[Authorization]                                        = None
}

@Singleton
class JenkinsBuildConnector @Inject()(httpClient: HttpClient, repositoryJobsConfig: RepositoryJobsConfig)
    extends JenkinsConnector {
  override val host: String            = repositoryJobsConfig.ciBuildUrl
  override val http: HttpClient        = httpClient
  override val apiTreeSelector: String = apiTree(3)
  override type JenkinsResponseType = JenkinsBuildJobsResponse
  override implicit val jenkinsResponseReads: Reads[JenkinsResponseType] =
    JenkinsBuildJobsResponse.jenkinsBuildJobsResponseReads
  override def convertJenkinsResponse(jenkinsResponse: JenkinsResponseType): JenkinsJobsResponse =
    flattenJobs(jenkinsResponse)
  override val authorizationHeader: Option[Authorization] = {
    val authorizationValue =
      s"Basic ${BaseEncoding.base64().encode(s"${repositoryJobsConfig.username}:${repositoryJobsConfig.password}".getBytes("UTF-8"))}"
    Some(Authorization(authorizationValue))
  }
}

case class JenkinsJobsResponse(jobs: Seq[Job])

object JenkinsJobsResponse {
  implicit val jenkinsJobsResponseReads: Reads[JenkinsJobsResponse] = Json.reads[JenkinsJobsResponse]
}

case class JenkinsBuildJobsResponse(jobs: Seq[JobTree])

object JenkinsBuildJobsResponse {
  implicit val jenkinsBuildJobsResponseReads: Reads[JenkinsBuildJobsResponse] = Reads { json =>
    (json \ "jobs")
      .validate(Reads.seq[Option[JobTree]](jobTreeReads))
      .map(jobs => JenkinsBuildJobsResponse(jobs.flatten))
  }

  def flattenJobs(jenkinsBuildJobsResponse: JenkinsBuildJobsResponse): JenkinsJobsResponse = {
    def _flattenJobs(jobTree: Seq[JobTree], acc: Seq[Job]): Seq[Job] =
      jobTree match {
        case Nil => acc
        case first :: rest =>
          first match {
            case job: Job        => _flattenJobs(rest, job +: acc)
            case Folder(_, jobs) => _flattenJobs(jobs ++ rest, acc)
          }
      }
    val jobs = _flattenJobs(jenkinsBuildJobsResponse.jobs, Seq.empty)
    JenkinsJobsResponse(jobs)
  }
}
