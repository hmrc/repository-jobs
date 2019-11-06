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

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import java.util.concurrent.TimeUnit

import javax.inject.{Inject, Singleton}
import scala.concurrent.duration.Duration
import play.Logger
import uk.gov.hmrc.mongo.component.MongoComponent

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import uk.gov.hmrc.mongo.lock._

@Singleton
class Scheduler @Inject()(
                           repositoryJobsService: RepositoryJobsService,
                           mongoLockRepo: MongoLockRepository,
                           metrics: Metrics,
                           actorSystem: ActorSystem)
  extends MongoLockService {

  override val lockId: String = "repository-jobs-scheduled-job"

  override val mongoLockRepository: MongoLockRepository = mongoLockRepo

  override val ttl: Duration = Duration(30, TimeUnit.MINUTES)

  def startUpdatingJobsModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")

    actorSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateRepositoryJobsModel()
    }
  }

  private def updateRepositoryJobsModel(): Future[Unit] =
    attemptLockWithRelease {
      repositoryJobsService.update.map(scheduledUpdateMetrics)
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Logger.warn("Failed to obtain lock. Another process may have it.")
      }
    }

  private def scheduledUpdateMetrics(updateResult: UpdateResult): Unit =
    try {
      metrics.defaultRegistry
        .counter("scheduler.success")
        .inc(updateResult.nSuccesses)
      metrics.defaultRegistry
        .counter("scheduler.failure")
        .inc(updateResult.nFailures)
    } catch {
      case NonFatal(ex) => Logger.error("Scheduler metrics couldn't be updated", ex)
    }

}
