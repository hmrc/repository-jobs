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

import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import org.joda.time.Duration
import play.Logger
import play.libs.Akka
import play.modules.reactivemongo.{MongoDbConnection, ReactiveMongoComponent}
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import uk.gov.hmrc.http.HttpGet

sealed trait JobResult
case class Error(message: String, ex : Throwable) extends JobResult {
  Logger.error(message, ex)
}
case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}
case class Info(message: String) extends JobResult {
  Logger.info(message)
}


@Singleton
class Scheduler @Inject()(repositoryJobsService: RepositoryJobsService,
                          reactiveMongoComponent: ReactiveMongoComponent,
                          metrics: Metrics,
                          actorSystem: ActorSystem) extends LockKeeper  {

  override def lockId: String = "repository-jobs-scheduled-job"

  override def repo: LockRepository = LockMongoRepository(reactiveMongoComponent.mongoConnector.db)

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(15)

  def startUpdatingJobsModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")

    actorSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateRepositoryJobsModel
    }
  }


  private def updateRepositoryJobsModel: Future[JobResult] = {
    tryLock {
      Logger.info(s"Starting mongo update")

      repositoryJobsService.update.map { result =>
        val total = result.toList.length
        val failureCount = result.count(r => !r)
        val successCount = total - failureCount

        metrics.defaultRegistry.counter("scheduler.success").inc(successCount)
        metrics.defaultRegistry.counter("scheduler.failure").inc(failureCount)

        Info(s"Added $successCount and encountered $failureCount failures")
      }.recover { case ex =>
        Error(s"Something went wrong during the mongo update:", ex)
      }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }
  }

}

