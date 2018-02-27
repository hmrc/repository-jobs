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

import akka.actor.ActorSystem
import com.kenshoo.play.metrics.Metrics
import java.util.concurrent.TimeUnit
import javax.inject.{Inject, Singleton}
import org.joda.time.Duration
import play.Logger
import play.modules.reactivemongo.ReactiveMongoComponent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal
import uk.gov.hmrc.lock.{LockKeeper, LockMongoRepository, LockRepository}

sealed trait JobResult
case class Error(message: String, ex: Throwable) extends JobResult {
  Logger.error(message, ex)
}
case class Warn(message: String) extends JobResult {
  Logger.warn(message)
}
case class Info(message: String) extends JobResult {
  Logger.info(message)
}
@Singleton
class Scheduler @Inject()(
  repositoryJobsService: RepositoryJobsService,
  reactiveMongoComponent: ReactiveMongoComponent,
  metrics: Metrics,
  actorSystem: ActorSystem)
    extends LockKeeper {

  override def lockId: String = "repository-jobs-scheduled-job"

  override def repo: LockRepository =
    LockMongoRepository(reactiveMongoComponent.mongoConnector.db)

  override val forceLockReleaseAfter: Duration = Duration.standardMinutes(5)

  def startUpdatingJobsModel(interval: FiniteDuration): Unit = {
    Logger.info(s"Initialising mongo update every $interval")

    actorSystem.scheduler.schedule(FiniteDuration(1, TimeUnit.SECONDS), interval) {
      updateRepositoryJobsModel()
    }
  }

  private def updateRepositoryJobsModel(): Future[JobResult] =
    tryLock {
      Logger.info(s"Starting mongo update")

      repositoryJobsService.update
        .map {
          case UpdateResult(nSuccesses, nFailures) =>
            metrics.defaultRegistry
              .counter("scheduler.success")
              .inc(nSuccesses)
            metrics.defaultRegistry
              .counter("scheduler.failure")
              .inc(nFailures)

            Info(s"Added $nSuccesses and encountered $nFailures failures")
        }
        .recoverWith {
          case NonFatal(ex) =>
            Error(s"Something went wrong during the mongo update:", ex)
            Future.failed(ex)
        }
    } map { resultOrLocked =>
      resultOrLocked getOrElse {
        Warn("Failed to obtain lock. Another process may have it.")
      }
    }

}
