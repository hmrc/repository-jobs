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
import play.api.libs.json.{JsError, JsSuccess, Json}
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext.fromLoggingDetails
import uk.gov.hmrc.play.http.{HeaderCarrier, HttpGet}

import scala.concurrent.Future
import scalaj.http.{Http, HttpResponse}

case class BuildResponse(description: String, duration: Int, id: String, number: Int, result: String,
                         timestamp: Long, url: String, builtOn: String)

case class UserRemoteConfig(url: String)
case class Scm(userRemoteConfigs: Seq[UserRemoteConfig])
case class Job(name: String, url: String, allBuilds: Seq[BuildResponse], scm: Scm)
case class JenkinsJobsResponse(jobs: Seq[Job])

trait JenkinsConnector {

  def http: HttpGet

  def jenkinsBaseUrl: String

  implicit val buildReads = Json.format[BuildResponse]
  implicit val userRemoteConfigReads = Json.format[UserRemoteConfig]
  implicit val scmReads = Json.format[Scm]
  implicit val jobReads = Json.format[Job]
  implicit val jenkinsReads = Json.format[JenkinsJobsResponse]

  val buildsUrl = "/api/json?tree=" + java.net.URLEncoder.encode("jobs[name,url,allBuilds[id,description,duration,number,result,timestamp,url,builtOn],scm[userRemoteConfigs[url]]]", "UTF-8")

  def getBuilds: Future[JenkinsJobsResponse] = {
    implicit val hc = new HeaderCarrier()

    val url = jenkinsBaseUrl + buildsUrl
//    val x: Future[String] = http.GET[String](url).recover {
//      case ex =>
//        Logger.error(s"An error occurred when connecting to $url: ${ex.getMessage}", ex)
//        throw ex
//    }

    val x: HttpResponse[String] = Http(url).asString

    val result = Json.parse(x.body.replaceAll("[\\^\\x00-\\x09\\x27\\x11\\x12\\x14-\\x1F\\x7F]", "")).validate[JenkinsJobsResponse]

    result match {
      case q: JsSuccess[JenkinsJobsResponse] => println(Json.prettyPrint(Json.toJson(q.get)))
      case JsError(e) => println(e)
    }

    println("*" * 100)
    println(Json.prettyPrint(Json.toJson(result.get)))
    println("*" * 100)


    Future.successful(result.get)


//    x.map(y => Json.parse(y.replaceAll("[\\x00-\\x09\\x27\\x11\\x12\\x14-\\x1F\\x7F]", "")).as[JenkinsJobsResponse])

//    throw new RuntimeException("booo")
  }



}
