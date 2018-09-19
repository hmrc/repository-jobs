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
import uk.gov.hmrc.http.HeaderCarrier
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import scala.util.control.NonFatal

@Singleton
class RepositoryJobsService @Inject()(
  repository: BuildsRepository,
  jenkinsDevConnector: JenkinsCiDevConnector,
  jenkinsOpenConnector: JenkinsCiOpenConnector,
  jenkinsBuildConnector: JenkinsBuildConnector) {

  implicit val hc: HeaderCarrier = HeaderCarrier()

  def update: Future[UpdateResult] = {
    val devBuildsResponseF: Future[JenkinsJobsResponse]  = jenkinsDevConnector.getBuilds
    val openBuildsResponseF: Future[JenkinsJobsResponse] = jenkinsOpenConnector.getBuilds
    val buildsResponseF: Future[JenkinsJobsResponse]     = jenkinsBuildConnector.getBuilds

    Logger.info("Starting repository jobs update")
    (for {
      devBuildsResponse <- devBuildsResponseF
      _ = Logger.info(
        s"Fetched builds from jenkins ci-dev. Number of builds: ${devBuildsResponse.jobs.map(_.allBuilds.size).sum}")

      openBuildsResponse <- openBuildsResponseF
      _ = Logger.info(
        s"Fetched builds from jenkins ci-open. Number of builds: ${openBuildsResponse.jobs.map(_.allBuilds.size).sum}")

      buildsResponse <- buildsResponseF
      _ = Logger.info(
        s"Fetched builds from jenkins build. Number of builds: ${buildsResponse.jobs.map(_.allBuilds.size).sum}")

      allJobs = devBuildsResponse.jobs ++ openBuildsResponse.jobs ++ buildsResponse.jobs

      result <- repository.persist(convertToBuilds(allJobs))
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

  private def convertToBuilds(jobs: Seq[Job]): Seq[Build] =
    jobs.flatMap { job =>
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

  private def getGitUrl(job: Job) = job.scm match {
    case Some(scm) => scm.userRemoteConfigs.fold("")(_.head.url.getOrElse(""))
    case None      => ""
  }

}

case class UpdateResult(nSuccesses: Int, nFailures: Int)

object UpdateResult {
  implicit val format: OFormat[UpdateResult] = Json.format[UpdateResult]
}
