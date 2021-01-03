import sbt._
import xerial.sbt.Sonatype.autoImport.sonatypePublishToBundle
import com.typesafe.tools.mima.plugin.MimaPlugin.autoImport._

object Common {
  import Keys._

  private[this] val unusedWarnings = Def.setting(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) => Seq(
        "-Ywarn-unused-import"
      )
      case Some((2, _)) => Seq(
        "-Ywarn-unused:imports"
      )
      case _ =>
        Nil
    }
  )

  val Scala212 = "2.12.12"

  val settings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq(
    organization := "ws.unfiltered",

    crossScalaVersions := Seq("2.13.4", Scala212, "2.11.12"),

    scalaVersion := Scala212,

    mimaPreviousArtifacts := {
      (0 to 1).map { v =>
        organization.value %% moduleName.value % s"0.10.$v"
      }.toSet
    },

    scalacOptions ++=
      Seq("-encoding", "utf8", "-deprecation", "-unchecked", "-feature"),

    scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
      case Some((2, _)) =>
        Seq("-Xcheckinit")
    }.toList.flatten,

    scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
      case Some((2, v)) if v <= 12 =>
        Seq("-Ywarn-adapted-args", "-Xfuture")
    }.toList.flatten,

    scalacOptions ++= unusedWarnings.value,

    fork in Test := true,

    scalacOptions in (Compile, doc) ++= {
      val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
      val base = (baseDirectory in LocalRootProject).value.getAbsolutePath
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          Nil
        case _ =>
          Seq(
            "-sourcepath",
            base,
            "-doc-source-url",
            "https://github.com/unfiltered/unfiltered/tree/" + hash + "€{FILE_PATH}.scala"
          )
      }
    },

    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6"),

    parallelExecution in Test := false, // :( test servers collide on same port

    homepage := Some(new java.net.URL("https://unfiltered.ws")),

    publishMavenStyle := true,

    publishTo := sonatypePublishToBundle.value,

    publishArtifact in Test := false,

    licenses := Seq("MIT" -> url("https://www.opensource.org/licenses/MIT")),

    pomExtra := (
      <scm>
        <url>git@github.com:unfiltered/unfiltered.git</url>
        <connection>scm:git:git@github.com:unfiltered/unfiltered.git</connection>
      </scm>
      <developers>
        <developer>
          <id>hamnis</id>
          <name>Erlend Hamnaberg</name>
          <url>https://twitter.com/hamnis</url>
        </developer>
        <developer>
          <id>eed3si9n</id>
          <name>Eugene Yokota</name>
          <url>https://twitter.com/eed3si9n</url>
        </developer>
        <developer>
          <id>xuwei-k</id>
          <name>Kenji Yoshida</name>
          <url>https://twitter.com/xuwei_k</url>
        </developer>
        <developer>
          <id>omarkilani</id>
          <name>Omar Kilani</name>
          <url>https://twitter.com/omarkilani</url>
        </developer>
        <developer>
          <id>n8han</id>
          <name>Nathan Hamblen</name>
          <url>https://twitter.com/n8han</url>
        </developer>
        <developer>
          <id>softprops</id>
          <name>Doug Tangren</name>
          <url>https://twitter.com/softprops</url>
        </developer>
      </developers>
    )
  ) ++ Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) --= unusedWarnings.value
  )
}
