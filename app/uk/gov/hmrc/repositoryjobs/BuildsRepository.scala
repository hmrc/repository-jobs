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
import play.api.libs.functional.syntax._
import play.api.libs.json.Json.toJson
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.ReadPreference.primaryPreferred
import reactivemongo.api.commands.{Command, MultiBulkWriteResult}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.{BSONDocument, BSONElement, BSONObjectID}
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.{BSONFormats, ImplicitBSONHandlers, JSONSerializationPack}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.control.NonFatal

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
  implicit val formats: OFormat[Build] = Json.format[Build]
}

@Singleton
class BuildsRepository @Inject()(mongo: ReactiveMongoComponent)
    extends ReactiveRepository[Build, BSONObjectID](
      collectionName = "builds",
      mongo          = mongo.mongoConnector.db,
      domainFormat   = Build.formats) {

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("repositoryName" -> Descending), background = true),
    Index(key = Seq("jobName"        -> Descending, "timestamp" -> Descending), background = true)
  )

  def persist(builds: Seq[Build]): Future[UpdateResult] = {
    import ImplicitBSONHandlers._

    val commandDoc = Json.obj(
      "update" -> collection.name,
      "updates" -> builds.map { build =>
        Json.obj(
          "q"      -> Json.obj("jobName" -> build.jobName, "timestamp" -> build.timestamp),
          "u"      -> toJson(build),
          "upsert" -> true,
          "multi"  -> false)
      }
    )
    val runner = Command.run(JSONSerializationPack, FailoverStrategy.default)

    runner(mongo.mongoConnector.db(), runner.rawCommand(commandDoc))
      .one[BSONDocument](primaryPreferred)
      .map(bsonToJson)
      .map(_.as[UpdateResult])
  }

  private val bsonToJson: BSONDocument => JsValue =
    bson =>
      JsObject(bson.elements.map {
        case BSONElement(name, value) => name -> BSONFormats.toJSON(value)
      })

  private implicit val updateResultReads: Reads[UpdateResult] = (
    (__ \ "n").read[Int] and
      (__ \ "nModified").read[Int] and
      (__ \ "upserted").read[Seq[JsValue]].map(_.size)
  ).tupled.map {
    case (recordsTotal, modified, upserted) =>
      UpdateResult(
        modified + upserted,
        recordsTotal - modified - upserted
      )
  }

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
