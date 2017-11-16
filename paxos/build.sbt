
import sbtassembly.MergeStrategy

val akkaVersion = "2.5.6"
val logbackVersion = "1.2.3"
val shapelessVersion = "2.3.2"

val reverseConcat: MergeStrategy = new MergeStrategy {
  val name = "reverseConcat"
  def apply(tempDir: File, path: String, files: Seq[File]): Either[String, Seq[(File, String)]] =
    MergeStrategy.concat(tempDir, path, files.reverse)
}

lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.example",
      scalaVersion := "2.12.3",
      version      := "0.1.0"
    )),
    name := "paxos-iosr",
    mainClass in assembly := Some("agh.iosr.paxos.client.LocalClient"),
    test in assembly := {},
    assemblyJarName in assembly := "paxos-iosr.jar",

    assemblyMergeStrategy in assembly := {
      case fileName if fileName.toLowerCase == "reference.conf" => reverseConcat
      case other => MergeStrategy.defaultMergeStrategy(other)
    },

    libraryDependencies += "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % akkaVersion,
    libraryDependencies += "ch.qos.logback" % "logback-classic" % logbackVersion,
    libraryDependencies += "com.chuusai" %% "shapeless" % shapelessVersion,

    libraryDependencies += "com.typesafe.akka" %% "akka-testkit"  % akkaVersion % Test,
    libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % Test,
  )
