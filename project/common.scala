import sbt._
import xerial.sbt.Sonatype.autoImport.sonatypePublishToBundle
import com.typesafe.tools.mima.plugin.MimaKeys.mimaPreviousArtifacts
import com.typesafe.tools.mima.plugin.MimaKeys.mimaReportSignatureProblems

object Common {
  import Keys._

  private[this] val unusedWarnings = Def.setting(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) => Seq(
        "-Ywarn-unused:imports"
      )
      case _ =>
        Nil
    }
  )

  val Scala212 = "2.12.20"

  val settings: Seq[Setting[_]] = Def.settings(
    mimaPreviousArtifacts := Set(
      organization.value %% name.value % "0.12.0"
    ),

    mimaReportSignatureProblems := true,

    organization := "ws.unfiltered",

    crossScalaVersions := Seq("2.13.17", Scala212, "3.3.7"),

    scalaVersion := Scala212,

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

    Test / fork := true,

    Compile / doc / sources := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          (Compile / doc / sources).value
        case _ =>
          Nil // TODO enable scaladoc for Scala 3
      }
    },

    (Compile / doc / scalacOptions) ++= {
      val hash = sys.process.Process("git rev-parse HEAD").lineStream_!.head
      val base = (LocalRootProject / baseDirectory).value.getAbsolutePath
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

    Compile / javacOptions ++= Seq("-source", "1.8", "-target", "1.8"),

    Test / parallelExecution := false, // :( test servers collide on same port

    homepage := Some(new java.net.URL("https://unfiltered.ws")),

    publishMavenStyle := true,

    publishTo := sonatypePublishToBundle.value,

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
    (c / console / scalacOptions) --= unusedWarnings.value
  )
}
