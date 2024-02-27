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

  val Scala212 = "2.12.19"

  val settings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq(
    organization := "ws.unfiltered",

    crossScalaVersions := Seq("2.13.12", Scala212, "2.11.12"),

    scalaVersion := Scala212,

    mimaPreviousArtifacts := {
      (0 to 2).map { v =>
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
      case Some((3, _)) =>
        Seq(
          "-Xignore-scala2-macros",
          "-source",
          "3.0-migration",
        )
    }.toList.flatten,

    scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
      case Some((2, v)) if v <= 12 =>
        Seq("-Ywarn-adapted-args", "-Xfuture")
    }.toList.flatten,

    scalacOptions ++= unusedWarnings.value,

    Test / fork := true,

    libraryDependencySchemes += "org.scala-lang.modules" %% "scala-xml" % "always",

    allDependencies := {
      val values = allDependencies.value
      // workaround for
      // https://twitter.com/olafurpg/status/1346777651550285824
      // "Modules were resolved with conflicting cross-version suffixes"
      // "   org.scala-lang.modules:scala-xml _3.0.0-RC1, _2.13"
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((3, _)) =>
          values.map(_.exclude("org.scala-lang.modules", "scala-xml_2.13"))
        case _ =>
          values
      }
    },

    Compile / doc / sources := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          (Compile / doc / sources).value
        case _ =>
          Nil // TODO enable scaladoc for Scala 3
      }
    },

    testFrameworks --= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, _)) =>
          Nil
        case _ =>
          // specs2 does not support Scala 3
          // https://github.com/etorreborre/specs2/issues/848
          // TODO remove this setting when specs2 for Scala 3 released
          Seq(TestFrameworks.Specs2)
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

    Compile / javacOptions ++= Seq("-source", "1.6", "-target", "1.6"),

    Test / parallelExecution := false, // :( test servers collide on same port

    homepage := Some(new java.net.URL("https://unfiltered.ws")),

    publishMavenStyle := true,

    publishTo := sonatypePublishToBundle.value,

    Test / publishArtifact := false,

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
