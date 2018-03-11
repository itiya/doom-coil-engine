import Dependencies._

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.3",
      version      := "0.1.0-SNAPSHOT"
    )),
    name := "Hello",
    libraryDependencies ++= Seq(
      scalaTest % Test,
      "com.github.pureconfig" %% "pureconfig" % "0.9.0",
      "org.scalaj" %% "scalaj-http" % "2.3.0",
      "com.typesafe.play" %% "play-json" % "2.6.7"
    )
  )
