name := "akkaTraining"

version := "0.1"

scalaVersion := "2.12.3"

organization := "com.manning"

libraryDependencies ++= {
  val akkaVersion = "2.5.4"
  Seq(
    "com.typesafe.akka"       %%  "akka-actor"   % akkaVersion,
    "com.typesafe.akka"       %%  "akka-slf4j"   % akkaVersion,
    "com.typesafe.akka"       %%  "akka-stream" % akkaVersion,
    "com.typesafe.akka"       %%  "akka-testkit" % akkaVersion   % "test",
    "org.scalatest"           %%  "scalatest"    % "3.0.0"       % "test"
  )
}

