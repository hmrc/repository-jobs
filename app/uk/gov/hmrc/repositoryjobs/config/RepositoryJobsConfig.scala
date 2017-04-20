package uk.gov.hmrc.repositoryjobs.config

import java.nio.file.{Files, Paths}

import play.api.{Logger, Play}


object RepositoryJobsConfig {


  val schedulerEnabled = Play.current.configuration.getBoolean("scheduler.enabled").getOrElse(false)

  lazy val deploymentsApiBase: String = config("deployments.api.url").get
  lazy val catalogueBaseUrl: String = config("catalogue.api.url").get

  lazy val gitEnterpriseHost: String = config("git.enterprise.host").get
  lazy val gitEnterpriseApiUrl: String = config("git.enterprise.api.url").get
  lazy val gitEnterpriseToken: String = config("git.enterprise.api.token").get

  lazy val gitOpenApiHost: String = config("git.open.host").get
  lazy val gitOpenApiUrl: String = config("git.open.api.url").get
  lazy val gitOpenToken: String = config("git.open.api.token").get

  lazy val gitOpenStorePath: String = storePath("open-local-git-store")
  lazy val gitEnterpriseStorePath: String = storePath("enterprise-local-git-store")

  private def config(path: String) = Play.current.configuration.getString(s"$path")

  private def storePath(prefix: String) = {
    val path = config("git.client.store.path")
      .fold(Files.createTempDirectory(prefix).toString)(x => Paths.get(x).resolve(prefix).toString)

    Logger.info(s"Store Path : $path")
    path
  }

}
