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
import org.mongodb.scala.model.Filters._
import org.mongodb.scala.model.{FindOneAndReplaceOptions, IndexModel, IndexOptions, Indexes}
import play.api.libs.json._
import uk.gov.hmrc.mongo.component.MongoComponent
import uk.gov.hmrc.mongo.play.PlayMongoCollection

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
private[repositoryjobs] class BuildsRepository @Inject()(mongo: MongoComponent)
  extends PlayMongoCollection(
    collectionName = "builds",
    mongoComponent = mongo,
    domainFormat = Build.formats,
    indexes = Seq(
      IndexModel(Indexes.descending("repositoryName"), IndexOptions().background(true)),
      IndexModel(Indexes.descending("jobName", "timestamp"), IndexOptions().background(true))
    )) {

  def getForRepository(repositoryName: String): Future[Seq[Build]] =
    collection.find(equal("repositoryName", repositoryName)).toFuture().map {
      case Nil => Seq()
      case data => data
    }

  private def persistOne(build: Build): Option[Future[UpdateResult]] = {
    for {
      jobName <- build.jobName
      timestamp <- build.timestamp
      filter = and(equal("jobName", jobName), equal("timestamp", timestamp))
      result = collection.findOneAndReplace(
        filter = filter,
        replacement = build,
        options = FindOneAndReplaceOptions().upsert(true))
        .toFuture()
        .map(_ => UpdateResult(nSuccesses = 1, nFailures = 0))
        .recover{
          case _ => UpdateResult(nSuccesses = 0, nFailures = 1)
        }
    } yield result
  }

  def persist(builds: Seq[Build]): Future[UpdateResult] = {
    Future.sequence(builds.flatMap(build => persistOne(build)))
      .map(_.foldLeft(UpdateResult(0, 0))((total, current) =>
        UpdateResult(total.nSuccesses + current.nSuccesses, total.nFailures + current.nFailures)))
  }
}
