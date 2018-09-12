import uk.gov.hmrc.DefaultBuildSettings.integrationTestSettings

val appName = "repository-jobs"

lazy val root = Project(appName, file("."))
  .enablePlugins(play.sbt.PlayScala, SbtAutoBuildPlugin, SbtGitVersioning, SbtDistributablesPlugin)
  .settings(
    scalaVersion        := "2.11.12",
    libraryDependencies ++= AppDependencies.compile ++ AppDependencies.test,
    PlayKeys.playDefaultPort := 8457,
    resolvers           :=
      Seq(
        Resolver.bintrayRepo("hmrc", "releases"),
        Resolver.typesafeRepo("releases")
      )
  )
  .configs(IntegrationTest)
  .settings(integrationTestSettings(): _*)