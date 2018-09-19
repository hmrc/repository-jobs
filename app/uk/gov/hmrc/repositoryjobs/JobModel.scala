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
import play.api.libs.json._
import play.api.libs.functional.syntax._
import uk.gov.hmrc.repositoryjobs.JobTree.jobTreeReads

case class BuildResponse(
  description: Option[String],
  duration: Option[Int],
  id: Option[String],
  number: Option[Int],
  result: Option[String],
  timestamp: Option[Long],
  url: Option[String],
  builtOn: Option[String])

object BuildResponse {
  implicit val buildResponseFormat: OFormat[BuildResponse] = Json.format[BuildResponse]
}

case class UserRemoteConfig(url: Option[String])

object UserRemoteConfig {
  implicit val userRemoteConfigFormat: OFormat[UserRemoteConfig] = Json.format[UserRemoteConfig]
}
case class Scm(userRemoteConfigs: Option[Seq[UserRemoteConfig]])

object Scm {
  implicit val scmFormat: OFormat[Scm] = Json.format[Scm]
}

sealed trait JobTree

object JobTree {
  implicit val jobTreeReads: Reads[Option[JobTree]] = Reads[Option[JobTree]] { json =>
    (json \ "_class").validate[String].flatMap {
      case "com.cloudbees.hudson.plugins.folder.Folder" => json.validate[Folder].map(Option.apply)
      case "hudson.model.FreeStyleProject"              => json.validate[Job].map(Option.apply)
      case _                                            => JsSuccess(None)
    }
  }
}

case class Job(name: Option[String], url: Option[String], allBuilds: Seq[BuildResponse], scm: Option[Scm])
    extends JobTree

object Job {
  implicit val jobFormat: OFormat[Job] = Json.format[Job]
}

case class Folder(name: Option[String], jobs: Seq[JobTree]) extends JobTree

object Folder {
  implicit lazy val folderReads: Reads[Folder] = (
    (__ \ "name").readNullable[String] and
      (__ \ "jobs").lazyRead(Reads.seq[Option[JobTree]]).map(_.flatten)
  )(Folder.apply _)
}
