name := "stream"
version := "1.0"
organization := "com.manning"
scalaVersion := "2.12.3"

libraryDependencies ++= {
  val akkaVersion = "2.5.25"
  val akkaHttpVersion = "10.0.10"
  Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    //<start id="stream-dependencies">
    "com.typesafe.akka" %% "akka-stream" % akkaVersion,
    //<end id="stream-dependencies">
    //<start id="stream-http-dependencies">
    "com.typesafe.akka" %% "akka-http-core"       % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http"            % akkaHttpVersion,
    "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
    "de.heikoseeberger" %% "akka-http-circe"      % "1.29.1",
    //<end id="stream-http-dependencies">
    //<start id="test-dependencies">
    "com.typesafe.akka" %% "akka-stream-testkit" % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-testkit"        % akkaVersion % "test",
    "com.typesafe.akka" %% "akka-http-testkit"    % akkaHttpVersion % Test,
    "org.scalatest"     %% "scalatest"           % "3.0.0" % "test",
    "org.mockito"       %% "mockito-scala"        % "1.5.7"         % Test
    //<end id="test-dependencies">
  )
}

scalacOptions ++= Seq(
  "-deprecation",
  "-unchecked",
  "-Xlint",
  "-Ywarn-unused",
  "-Ywarn-dead-code",
  "-feature",
  "-language:_"
)

//mainClass in reStart := Some("aia.stream.BufferLogsApp")
