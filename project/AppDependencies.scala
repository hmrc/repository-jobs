import play.core.PlayVersion
import play.sbt.PlayImport._
import sbt._

object AppDependencies {

  val compile = Seq(
    "uk.gov.hmrc" %% "play-reactivemongo" % "6.1.0",
    ws,
    "uk.gov.hmrc"   %% "bootstrap-play-25" % "3.3.0",
    "uk.gov.hmrc"   %% "play-url-binders"  % "2.1.0",
    "uk.gov.hmrc"   %% "mongo-lock"        % "5.0.0",
    "uk.gov.hmrc"   %% "domain"            % "4.1.0",
    "org.typelevel" %% "cats-core"         % "0.9.0"
  )

  val test = Seq(
    "uk.gov.hmrc"            %% "hmrctest"           % "2.3.0"             % "test,it",
    "org.scalatest"          %% "scalatest"          % "2.2.6"             % "test,it",
    "org.scalatestplus.play" %% "scalatestplus-play" % "1.5.0"             % "test,it",
    "org.scalacheck"         %% "scalacheck"         % "1.12.6"            % "test,it",
    "org.pegdown"            % "pegdown"             % "1.6.0"             % "test,it",
    "com.typesafe.play"      %% "play-test"          % PlayVersion.current % "test,it",
    "com.github.tomakehurst" % "wiremock"            % "1.52"              % "test,it",
    "org.mockito"            % "mockito-all"         % "1.10.19"           % "test,it",
    "uk.gov.hmrc"            %% "reactivemongo-test" % "2.0.0"             % "test,it"
  )

}
