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

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

class RepositoryJobsService(repository: BuildsRepository, connector: JenkinsConnector) {

  def key(jobName: String, timestamp: Long): String = {
    s"${jobName}_$timestamp"
  }

  def key(jobName: Option[String], timestamp: Option[Long]): String = {
    key(jobName.getOrElse("no-job-name"), timestamp.getOrElse(0l))
  }

  def update: Future[Seq[Boolean]] =
    for {
      buildsResponse <- connector.getBuilds
      existingBuilds <- repository.getAll
      jobsWithNewBuilds <- findJobsWithNewBuilds(buildsResponse, existingBuilds)
    } yield
      jobsWithNewBuilds


  def findJobsWithNewBuilds(buildsResponse: JenkinsJobsResponse, existingBuilds: Seq[Build]): Future[Seq[Boolean]] = {

    def buildAlreadyExists(job: Job, buildResponse: BuildResponse): Boolean =
      existingBuilds.exists(existingBuild => key(existingBuild.jobName, existingBuild.timestamp) == key(job.name, buildResponse.timestamp))

    def collectJobsWithUnsavedBuilds() = buildsResponse.jobs.map { (job: Job) =>
      job.copy(allBuilds = job.allBuilds.map(_.filterNot(buildResponse => buildAlreadyExists(job, buildResponse))))
    }

    def getGitUrl(job: Job) = job.scm match {
      case Some(scm) => scm.userRemoteConfigs.fold("")(_.head.url.getOrElse(""))
      case None => ""
    }

    Future.sequence {
      val jobsWithUnsavedBuilds: Seq[Job] = collectJobsWithUnsavedBuilds()

      jobsWithUnsavedBuilds.filter(_.allBuilds.nonEmpty).flatMap { job =>

        val gitUrl = getGitUrl(job)

        val repoName = Try(gitUrl.split("/").last.stripSuffix(".git")).toOption

        job.allBuilds.fold(Seq.empty[Future[Boolean]]) { (allBuilds: Seq[BuildResponse]) =>
          allBuilds.map(b => repository.add(Build(repoName, job.name, job.url, b.number, b.result, b.timestamp, b.duration, b.url, b.builtOn)))
        }
      }
    }
  }
}
