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

package uk.gov.hmrc.repositoryjobs.config

import javax.inject.{Inject, Singleton}
import play.api.Configuration

@Singleton
class RepositoryJobsConfig @Inject()(configuration: Configuration) {

  val schedulerEnabled =
    configuration.getBoolean("scheduler.enabled").getOrElse(false)

  def ciDevUrl: String =
    config("jobs.api.url.ci-dev").getOrElse(
      throw new RuntimeException("Error getting config value jobs.api.url.ci-dev"))

  def ciOpenUrl: String =
    config("jobs.api.url.ci-open").getOrElse(
      throw new RuntimeException("Error getting config value jobs.api.url.ci-open"))

  def ciBuildUrl: String =
    config("jobs.api.url.ci-build").getOrElse(
      throw new RuntimeException("Error getting config value jobs.api.url.ci-build"))

  def username: String =
    config("jobs.api.basicAuth.username").getOrElse(
      throw new RuntimeException("Error getting config value jobs.api.basicAuth.username"))

  def password: String =
    config("jobs.api.basicAuth.password").getOrElse(
      throw new RuntimeException("Error getting config value jobs.api.basicAuth.password"))

  private def config(path: String) = configuration.getString(s"$path")

}
