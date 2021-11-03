import sbt.Keys.libraryDependencies

name := "log-search"

version := "0.1"

scalaVersion := "2.13.6"

val logbackVersion = "1.3.0-alpha10"
val sfl4sVersion = "2.0.0-alpha5"
val typesafeConfigVersion = "1.4.1"
val apacheCommonIOVersion = "2.11.0"
val awsVersion = "2.17.66"
val scalacticVersion = "3.2.9"
val generexVersion = "1.0.2"

resolvers += Resolver.jcenterRepo

lazy val assemblySettings =
  assembly / assemblyMergeStrategy  := {
    case PathList("META-INF", xs @ _*) => MergeStrategy.discard
    case x => MergeStrategy.first
  }

val AkkaVersion = "2.6.8"
val AkkaStreamVersion = "2.6.17"
val AkkaHttpVersion = "10.2.6"


lazy val GrpcProto = (project in file("GrpcProto"))
  .settings(
    assemblySettings,
    Compile / PB.targets := Seq(
      scalapb.gen() -> (Compile / sourceManaged).value / "scalapb"
    ),
    libraryDependencies ++= Seq(
      "io.grpc" % "grpc-netty" % scalapb.compiler.Version.grpcJavaVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime-grpc" % scalapb.compiler.Version.scalapbVersion,
      "com.thesamet.scalapb" %% "scalapb-runtime" % scalapb.compiler.Version.scalapbVersion,
    )
  )


lazy val GrpcRestLambda = (project in file("GrpcRestLambda"))
  .settings(
    assemblySettings,
      libraryDependencies ++= Seq(
      "ch.qos.logback" % "logback-core" % logbackVersion,
      "ch.qos.logback" % "logback-classic" % logbackVersion,
      "org.slf4j" % "slf4j-api" % sfl4sVersion,
      "com.typesafe" % "config" % typesafeConfigVersion,
      "commons-io" % "commons-io" % apacheCommonIOVersion,
      "org.scalactic" %% "scalactic" % scalacticVersion,
      "org.scalatest" %% "scalatest" % scalacticVersion % Test,
      "org.scalatest" %% "scalatest-featurespec" % scalacticVersion % Test,
      "com.typesafe" % "config" % typesafeConfigVersion,
      "com.github.mifmif" % "generex" % generexVersion,
      "software.amazon.awssdk" % "s3" % awsVersion,
      "software.amazon.awssdk" % "lambda" % awsVersion,
      "com.amazonaws" % "aws-lambda-java-core" % "1.2.1",
      "com.amazonaws" % "aws-lambda-java-events" % "3.10.0",
      "com.fasterxml.jackson.core" % "jackson-databind" % "2.13.0",
      "org.json4s" %% "json4s-jackson" % "4.0.3",
      "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0",
      "com.typesafe.play" %% "play-json" % "2.9.2"
    )
  ).dependsOn(GrpcProto)

lazy val AkkaService = (project in file("AkkaService"))
  .settings(
    assemblySettings,
    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor-typed" % AkkaStreamVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaStreamVersion,
      "com.typesafe.akka" %% "akka-http" % AkkaHttpVersion,
      "com.typesafe.akka" %% "akka-http-spray-json" % AkkaHttpVersion,
      "org.json4s" %% "json4s-jackson" % "4.0.3",
      "com.thesamet.scalapb" %% "scalapb-json4s" % "0.12.0",
    )
  ).dependsOn(GrpcProto)

lazy val root = (project in file("."))
  .aggregate(GrpcProto,GrpcRestLambda,AkkaService)


