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
import play.api.libs.json.Json
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.commands.MultiBulkWriteResult
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal
import uk.gov.hmrc.mongo.ReactiveRepository

case class Build(
  repositoryName: Option[String],
  jobName: Option[String],
  jobUrl: Option[String],
  buildNumber: Option[Int],
  result: Option[String],
  timestamp: Option[Long],
  duration: Option[Int],
  buildUrl: Option[String],
  builtOn: Option[String])

object Build {
  implicit val formats = Json.format[Build]
}

@Singleton
class BuildsRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Build, BSONObjectID](
      collectionName = "builds",
      mongo          = mongo.mongoConnector.db,
      domainFormat   = Build.formats) {

  def bulkAdd(builds: Seq[Build]): Future[UpdateResult] =
    bulkInsert(builds) map {
      case MultiBulkWriteResult(_, n, _, _, writeErrors, _, _, _, _) =>
        UpdateResult(n, writeErrors.size)
    } recoverWith {
      case NonFatal(ex) =>
        Logger.error(s"An exception [$ex] occurred while performing a bulk insert of the following builds: [$builds]")
        Future.failed(ex)
    }

  def getForRepository(repositoryName: String): Future[Seq[Build]] =
    find("repositoryName" -> BSONDocument("$eq" -> repositoryName)) map {
      case Nil  => Seq()
      case data => data
    }

  def getAllByRepo: Future[Map[String, Seq[Build]]] =
    findAll() map { data =>
      data.groupBy(_.repositoryName.getOrElse("no-repo-name"))
    }

  def getAll: Future[Seq[Build]] = findAll()
}
