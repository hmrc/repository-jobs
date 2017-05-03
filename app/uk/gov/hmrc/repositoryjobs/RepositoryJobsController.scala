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

import play.api.libs.json.Json
import uk.gov.hmrc.play.microservice.controller.BaseController
import uk.gov.hmrc.play.http.logging.MdcLoggingExecutionContext._
import play.api.mvc._
import play.modules.reactivemongo.MongoDbConnection

import scala.concurrent.Future

object RepositoryJobsController extends RepositoryJobsController with MongoDbConnection {
	override val buildRepository: BuildsRepository with Object = new BuildsMongoRepository(db)
}

trait RepositoryJobsController extends BaseController {
	val buildRepository: BuildsRepository


	def builds(repositoryName: String) = Action.async { implicit request =>
		buildRepository.getForRepository(repositoryName).map {
			case Nil => NotFound(s"No build found for '$repositoryName'")
			case builds => Ok(Json.toJson(builds))
		}
	}
	
	def hello() = Action.async { implicit request =>
		Future.successful(Ok("Hello world"))
	}
}
