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

class RepositoryJobsService(repository: BuildsRepository, connector: JenkinsConnector) {

  def key(jobName: String, timestamp: Double): String = {
    s"${jobName}_$timestamp"
  }

  def update: Future[Seq[Boolean]] =
    for {
      buildsResponse <- connector.getBuilds
      existingBuilds <- repository.getAll
      jobsWithNewBuilds <- findJobsWithNewBuilds(buildsResponse, existingBuilds)
    } yield
      jobsWithNewBuilds


  def findJobsWithNewBuilds(buildsResponse: JenkinsJobsResponse, existingBuilds: Seq[Build]): Future[Seq[Boolean]] = {

    def buildAlreadyExists(job: Job, buildResponse: BuildResponse) =
      existingBuilds.exists(existingBuild => key(existingBuild.jobName, existingBuild.timestamp) == key(job.name, buildResponse.timestamp))

    Future.sequence {
      buildsResponse.jobs.map { (job: Job) =>
        job.copy(allBuilds = job.allBuilds.filterNot(buildResponse => buildAlreadyExists(job, buildResponse)))
      }.filter(_.allBuilds.nonEmpty).flatMap { job =>
        val gitUrl = job.scm.userRemoteConfigs.head.url
        val repoName = gitUrl.split("/").last.stripSuffix(".git")

        job.allBuilds.map { b =>
          repository.add(Build(repoName, job.name, job.url, b.number, b.result, b.timestamp, b.duration, b.url, b.builtOn))
        }

      }
    }
  }
}
