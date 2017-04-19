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

  def update() : Future[Unit] =
    for {
      buildsResponse <- connector.getBuilds
      existingBuilds <- repository.getAll
    } yield {
      jobsWithNewBuilds(buildsResponse, existingBuilds).flatMap { job =>
        val gitUrl = job.scm.userRemoteConfigs.head.url
        val repoName = gitUrl.split("/").last.stripSuffix(".git")

        job.allBuilds.map { b =>
          repository.add(Build(repoName, job.name, job.url, b.number, b.result, b.timestamp, b.duration, b.url, b.builtOn))
        }
      }
    }

  def jobsWithNewBuilds(buildsResponse: JenkinsJobsResponse, existingBuilds: Seq[Build]) = {
    buildsResponse.jobs.map { job =>
      job.copy(allBuilds = job.allBuilds.filterNot { b =>
        existingBuilds.exists(e => key(job.name, e.timestamp) == key(job.name, b.timestamp))
      })
    }.filter(_.allBuilds.nonEmpty)
  }
}
