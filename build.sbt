name := "slackbot2"

version := "1.0"

lazy val `slackbot2` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.8"

resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

resolvers +=
  Resolver.url("Objectify Play Repository", url("http://deadbolt.ws/releases/"))(Resolver.ivyStylePatterns)


val akkaVersion = "2.4.14"
val akkaHttpVersion = "10.0.0"


libraryDependencies ++= Seq( jdbc,  cache , ws, filters,
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % "test",
  "com.typesafe.akka" %% "akka-http-core" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http" % akkaHttpVersion,
  "com.typesafe.akka" %% "akka-http-spray-json" % akkaHttpVersion,
  "com.github.gilbertw1" %% "slack-scala-client" % "0.2.0",
  "mysql" % "mysql-connector-java" % "5.1.40",
  "com.typesafe.play" %% "play-slick" % "2.0.2",
  "com.zaxxer" % "HikariCP" % "2.5.1",
  "com.h2database" % "h2" % "1.4.193"

)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

// resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"
