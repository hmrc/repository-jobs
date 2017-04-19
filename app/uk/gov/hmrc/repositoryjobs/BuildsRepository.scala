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

import play.api.libs.json.Json
import reactivemongo.api.DB
import reactivemongo.bson.{BSONDocument, BSONObjectID}
import uk.gov.hmrc.mongo.ReactiveRepository

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

case class Build(repositoryName: String, jobName: String, jobUrl: String, buildNumber: Int, result: String,
                 timestamp: Double, duration: Int, buildUrl: String, builtOn: String)

object Build {
  val formats = Json.format[Build]
}

trait BuildsRepository {
  def add(build: Build): Future[Boolean]
  def getForRepository(repoName: String): Future[Seq[Build]]
  def getAllByRepo : Future[Map[String, Seq[Build]]]
  def getAll: Future[Seq[Build]]
}

class BuildsMongoRepository (mongo: () => DB)
  extends ReactiveRepository[Build, BSONObjectID] (
    collectionName = "builds",
    mongo = mongo,
    domainFormat = Build.formats) with BuildsRepository {

  def add(build: Build): Future[Boolean] = {
    insert(build) map {
      case lastError if lastError.inError => throw lastError
      case _ => true
    }
  }

  def getForRepository(repoName: String): Future[Seq[Build]] = {
    find("repositoryName" -> BSONDocument("$eq" -> repoName)) map {
      case Nil => Seq()
      case data => data
    }
  }

  def getAllByRepo : Future[Map[String, Seq[Build]]] =
  {
    findAll() map { data =>
      data.groupBy(_.repositoryName)
    }
  }

  def getAll = findAll()
}
