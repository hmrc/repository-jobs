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
import play.api.libs.json.Reads._
import play.api.libs.json._
import play.modules.reactivemongo.ReactiveMongoComponent
import reactivemongo.api.FailoverStrategy
import reactivemongo.api.ReadPreference.primaryPreferred
import reactivemongo.api.commands.{Command, MultiBulkWriteResult}
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.{BSONDocument, BSONElement, BSONObjectID}
import reactivemongo.play.json.BSONFormats.toJSON
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack
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
class BuildsRepository private[repositoryjobs] (mongo: ReactiveMongoComponent, buildToJson: Build => JsValue)
    extends ReactiveRepository[Build, BSONObjectID](
      collectionName = "builds",
      mongo          = mongo.mongoConnector.db,
      domainFormat   = Build.formats) {

  @Inject() def this(mongo: ReactiveMongoComponent) = this(mongo, (build: Build) => Json.toJson(build))

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("repositoryName" -> Descending), background = true),
    Index(key = Seq("jobName"        -> Descending, "timestamp" -> Descending), background = true)
  )

  def persist(builds: Seq[Build]): Future[UpdateResult] = builds match {
    case Nil =>
      Future.successful(UpdateResult(nSuccesses = 0, nFailures = 0))
    case _ =>
      val commandDocument = Json.obj(
        "update" -> collection.name,
        "updates" -> builds.map { build =>
          Json.obj(
            "q"      -> Json.obj("jobName" -> build.jobName, "timestamp" -> build.timestamp),
            "u"      -> buildToJson(build),
            "upsert" -> true,
            "multi"  -> false
          )
        }
      )

      val runner = Command.run(JSONSerializationPack, FailoverStrategy.default)

      runner(mongo.mongoConnector.db(), runner.rawCommand(commandDocument))
        .one[BSONDocument](primaryPreferred)
        .map(bsonToJson)
        .map(_.as[UpdateResult](updateResultReads(builds)))
  }

  private def bsonToJson(bson: BSONDocument): JsValue =
    JsObject(bson.elements.map {
      case BSONElement(name, value) => name -> toJSON(value)
    })

  private def updateResultReads(builds: Seq[Build]): Reads[UpdateResult] = {
    implicit val errorReads: Reads[(Int, String)] = (
      (__ \ "index").read[Int] and
        (__ \ "errmsg").read[String]
    ).tupled

    val logError: ((Int, String)) => Unit = {
      case (recordIndex, errorMessage) =>
        Logger.error(s"${builds(recordIndex)} couldn't be stored in Mongo; error: $errorMessage")
    }

    (
      (__ \ "nModified").read[Int] and
        (__ \ "upserted").readNullable[Seq[JsValue]].map(_.map(_.size).getOrElse(0)) and
        (__ \ "writeErrors").readNullable[Seq[(Int, String)]].map(_.getOrElse(Nil))
    ).tupled.map {
      case (modified, upserted, errors) =>
        errors foreach logError

        UpdateResult(
          nSuccesses = modified + upserted,
          nFailures  = errors.size
        )
    }
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
