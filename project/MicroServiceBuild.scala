import sbt._
import play.sbt.PlayImport._
import play.core.PlayVersion
import uk.gov.hmrc.SbtAutoBuildPlugin
import uk.gov.hmrc.sbtdistributables.SbtDistributablesPlugin
import uk.gov.hmrc.versioning.SbtGitVersioning

object MicroServiceBuild extends Build with MicroService {

  val appName = "repository-jobs"

  override lazy val appDependencies: Seq[ModuleID] = compile ++ test()

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    ws,
    "uk.gov.hmrc"   %% "bootstrap-play-25" % "1.5.0",
    "uk.gov.hmrc"   %% "play-url-binders"  % "2.1.0",
    "uk.gov.hmrc"   %% "mongo-lock"        % "5.0.0",
    "uk.gov.hmrc"   %% "domain"            % "4.1.0",
    "org.typelevel" %% "cats-core"         % "0.9.0"
  )

  def test(scope: String = "test,it") = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "2.3.0"             % scope,
    "org.scalatest"          %% "scalatest"          % "2.2.6"             % scope,
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0"             % scope,
    "org.scalacheck"         %% "scalacheck"         % "1.12.6"            % scope,
    "org.pegdown"            % "pegdown"             % "1.6.0"             % scope,
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % scope,
    "com.github.tomakehurst" % "wiremock"            % "1.52"              % scope,
    "org.mockito"            % "mockito-all"         % "1.10.19"           % scope,
    "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0"             % scope
  )

}
