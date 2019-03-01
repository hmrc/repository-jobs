import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    ws,
    "uk.gov.hmrc"   %% "bootstrap-play-25" % "4.9.0",
    "uk.gov.hmrc"   %% "mongo-lock"        % "6.10.0-play-25",
    "uk.gov.hmrc"   %% "domain"            % "5.3.0",
    "org.typelevel" %% "cats-core"         % "0.9.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "3.5.0-play-25"     % "test,it",
    "org.scalatest"          %% "scalatest"          % "3.0.5"             % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "2.0.1"             % "test,it",
    "org.scalacheck"         %% "scalacheck"         % "1.13.4"            % "test,it",
    "org.pegdown"            % "pegdown"             % "1.6.0"             % "test,it",
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % "test,it",
    "com.github.tomakehurst" % "wiremock"            % "1.52"              % "test,it",
    "org.mockito"            % "mockito-all"         % "1.10.19"           % "test,it",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "4.8.0-play-25"     % "test,it"
  )

}
