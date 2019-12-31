import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val hmrcMongoVersion = "0.20.0"

  val compile = Seq(
    ws,
    "uk.gov.hmrc"         %% "bootstrap-play-26"  % "1.3.0",
    "uk.gov.hmrc.mongo"   %% "hmrc-mongo-play-26" % hmrcMongoVersion,
    "uk.gov.hmrc"         %% "domain"             % "5.3.0",
    "org.typelevel"       %% "cats-core"          % "0.9.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "bootstrap-play-26"         % "1.3.0"              % "test" classifier "tests",
    "org.scalatest"          %% "scalatest"                 % "3.1.0"              % Test,
    "org.scalatestplus.play" %% "scalatestplus-play"        % "3.1.3"              % Test,
    "org.mockito"            %% "mockito-scala"             % "1.10.2"             % Test,
    "org.scalatestplus"      %% "scalatestplus-scalacheck"  % "3.1.0.0-RC2"        % Test,
    "org.scalacheck"         %% "scalacheck"                % "1.14.3"             % Test,
    "com.typesafe.play"      %% "play-test"                 % PlayVersion.current  % Test,
    "com.github.tomakehurst" % "wiremock"                   % "1.58"               % Test,
    "uk.gov.hmrc.mongo"      %% "hmrc-mongo-test"           % hmrcMongoVersion     % Test
  )
}
