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
import uk.gov.hmrc.repositoryjobs.config.RepositoryJobsConfig

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class RepositoryJobsService @Inject()(
  repository: BuildsRepository,
  jenkinsDevConnector: JenkinsCiDevConnector,
  jenkinsOpenConnector: JenkinsCiOpenConnector,
  repositoryJobsConfig: RepositoryJobsConfig) {

  def key(jobName: String, timestamp: Long): String =
    s"${jobName}_$timestamp"

  def key(jobName: Option[String], timestamp: Option[Long]): String =
    key(jobName.getOrElse("no-job-name"), timestamp.getOrElse(0l))

  def update: Future[UpdateResult] = {
    Logger.info("Starting repository jobs update")
    (for {
      devBuildsResponse <- jenkinsDevConnector.getBuilds
      _ = Logger.info(
        s"Fetched builds from jenkins ci-dev. Number of builds: ${devBuildsResponse.jobs.map(_.allBuilds.size).sum}")

      openBuildsResponse <- jenkinsOpenConnector.getBuilds
      _ = Logger.info(
        s"Fetched builds from jenkins ci-open. Number of builds: ${openBuildsResponse.jobs.map(_.allBuilds.size).sum}")

      existingBuilds <- repository.getAll
      _ = Logger.info(s"Fetched existing repositories from mongo.  Number of existing builds: ${existingBuilds.size}")

      newBuildsFromDev = getBuilds(devBuildsResponse.jobs, existingBuilds)
      _ = Logger.info(
        s"Calculated new builds to be saved from jenkins ci-dev. Number of new builds: ${newBuildsFromDev.size}")

      newBuildsFromOpen = getBuilds(openBuildsResponse.jobs, existingBuilds)
      _ = Logger.info(
        s"Calculated new builds to be saved from jenkins ci-open. Number of new builds: ${newBuildsFromOpen.size}")

      result <- repository.bulkAdd(newBuildsFromDev ++ newBuildsFromOpen)
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
    val setExistingBuilds = existingBuilds.map(b => (b.jobName, b.timestamp)).toSet

    def buildAlreadyExists(job: Job, buildResponse: BuildResponse): Boolean =
      setExistingBuilds.contains((job.name, buildResponse.timestamp))

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
