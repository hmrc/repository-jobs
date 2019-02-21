/*
 * Copyright 2019 HM Revenue & Customs
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
import reactivemongo.api.commands.Command
import reactivemongo.api.indexes.Index
import reactivemongo.api.indexes.IndexType.Descending
import reactivemongo.bson.{BSONDocument, BSONElement, BSONObjectID}
import reactivemongo.play.json.BSONFormats.toJSON
import reactivemongo.play.json.ImplicitBSONHandlers._
import reactivemongo.play.json.JSONSerializationPack
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

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

  // According to mongo documentation this is the limit of operations in one batch
  // https://docs.mongodb.com/v3.2/reference/limits/#Write-Command-Operation-Limit-Size
  private val MAX_BATCH_SIZE = 1000

  override def indexes: Seq[Index] = Seq(
    Index(key = Seq("repositoryName" -> Descending), background = true),
    Index(key = Seq("jobName"        -> Descending, "timestamp" -> Descending), background = true)
  )

  def getForRepository(repositoryName: String): Future[Seq[Build]] =
    find("repositoryName" -> BSONDocument("$eq" -> repositoryName)) map {
      case Nil  => Seq()
      case data => data
    }

  def persist(builds: Seq[Build]): Future[UpdateResult] = builds match {
    case Nil =>
      Future.successful(UpdateResult(nSuccesses = 0, nFailures = 0))
    case _ =>
      builds.grouped(MAX_BATCH_SIZE).foldLeft(Future.successful(UpdateResult(0, 0))) { (futAccumulatedResults, chunk) =>
        for {
          accumulatedResults <- futAccumulatedResults
          updateResult       <- persistChunk(chunk)
        } yield
          UpdateResult(
            nSuccesses = accumulatedResults.nSuccesses + updateResult.nSuccesses,
            nFailures  = accumulatedResults.nFailures + updateResult.nFailures
          )
      }
  }

  private def persistChunk(builds: Seq[Build]): Future[UpdateResult] = {
    val updateCommand = Json.obj(
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

    execute(updateCommand)
      .map(_.as[CommandResult])
      .map(logErrors(builds))
      .map(commandResult => UpdateResult(commandResult.nSuccesses, commandResult.errors.size))
  }

  private def execute(command: JsObject): Future[JsValue] = {
    def bsonToJson(bson: BSONDocument): JsValue =
      JsObject(bson.elements.map {
        case BSONElement(name, value) => name -> toJSON(value)
      })

    val runner = Command.run(JSONSerializationPack, FailoverStrategy.default)

    runner(mongo.mongoConnector.db(), runner.rawCommand(command))
      .one[BSONDocument](primaryPreferred)
      .map(bsonToJson)
  }

  private case class CommandResult(nSuccesses: Int, errors: Seq[CommandError])
  private case class CommandError(recordIndex: Int, message: String)

  private implicit val updateResultReads: Reads[CommandResult] = {
    implicit val errorReads: Reads[CommandError] = (
      (__ \ "index").read[Int] and
        (__ \ "errmsg").read[String]
    )(CommandError.apply _)

    (
      (__ \ "nModified").readNullable[Int].map(_.getOrElse(0)) and
        (__ \ "upserted").readNullable[Seq[JsValue]].map(_.map(_.size).getOrElse(0)) and
        (__ \ "writeErrors").readNullable[Seq[CommandError]].map(_.getOrElse(Nil))
    ).tupled.map {
      case (modified, upserted, errors) =>
        CommandResult(
          nSuccesses = modified + upserted,
          errors     = errors
        )
    }
  }

  private def logErrors(builds: Seq[Build])(commandResult: CommandResult): CommandResult = {
    commandResult.errors foreach {
      case CommandError(recordIndex, errorMessage) =>
        Logger.error(s"${builds(recordIndex)} couldn't be stored in Mongo; error: $errorMessage")
    }
    commandResult
  }
}
