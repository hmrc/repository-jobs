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
import play.Logger
import play.api.libs.json.{Json, OFormat}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class RepositoryJobsService @Inject()(repository: BuildsRepository, connector: JenkinsConnector) {

  def key(jobName: String, timestamp: Long): String =
    s"${jobName}_$timestamp"

  def key(jobName: Option[String], timestamp: Option[Long]): String =
    key(jobName.getOrElse("no-job-name"), timestamp.getOrElse(0l))

  def update: Future[UpdateResult] = {
    Logger.info("Starting repository jobs update")
    (for {
      buildsResponse <- connector.getBuilds
      _ = Logger.info(s"fetched builds from jenkins. Number of jobs: ${buildsResponse.jobs.map(_.allBuilds.size).sum}")

      existingBuilds <- repository.getAll
      _ = Logger.info(s"fetched existing repositories from mongo.  Number of existing builds: ${existingBuilds.size}")

      buildsToSave = getBuilds(buildsResponse.jobs, existingBuilds)
      _            = Logger.info(s"calculated new builds to be saved.  Number of new builds: $buildsToSave")

      result <- repository.bulkAdd(buildsToSave)
    } yield result)
      .map { updateResult =>
        Logger.info(s"Completed repository jobs update: $updateResult")
        updateResult
      } recoverWith {
      case NonFatal(ex) =>
        Logger.error("Unable to update repository jobs", ex)
        Future.failed(ex)
    }
  }

  private[repositoryjobs] def getBuilds(jobs: Seq[Job], existingBuilds: Seq[Build]): Seq[Build] = {

    def buildAlreadyExists(job: Job, buildResponse: BuildResponse): Boolean =
      existingBuilds.exists(
        existingBuild =>
          key(existingBuild.jobName, existingBuild.timestamp) ==
            key(job.name, buildResponse.timestamp))

    val jobsWithNewBuilds =
      jobs.map { (job: Job) =>
        job.copy(
          allBuilds = job.allBuilds
            .filterNot(buildAlreadyExists(job, _))
            .filter(_.result.isDefined))
      }

    val builds =
      jobsWithNewBuilds.flatMap { job =>
        val gitUrl   = getGitUrl(job)
        val repoName = Try(gitUrl.split("/").last.stripSuffix(".git")).toOption

        job.allBuilds.map { buildResponse =>
          Build(
            repoName,
            job.name,
            job.url,
            buildResponse.number,
            buildResponse.result,
            buildResponse.timestamp,
            buildResponse.duration,
            buildResponse.url,
            buildResponse.builtOn
          )
        }
      }

    builds
  }

  private def getGitUrl(job: Job) = job.scm match {
    case Some(scm) => scm.userRemoteConfigs.fold("")(_.head.url.getOrElse(""))
    case None      => ""
  }

}

case class UpdateResult(nSuccesses: Int, nFailures: Int)

object UpdateResult {
  implicit val format: OFormat[UpdateResult] = Json.format[UpdateResult]
}
