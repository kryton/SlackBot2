name := "slackbot2"

version := "1.0"

lazy val `slackbot2` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

val akkaVersion = "2.4.12"
val akkaHttpVersion = "2.4.11"


libraryDependencies ++= Seq( javaJdbc ,  cache , javaWs,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-experimental" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json-experimental" % akkaHttpVersion,
  "com.github.gilbertw1" %% "slack-scala-client" % "0.2.0-SNAPSHOT")

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

// resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
