import sbt._

object Common {
  import Keys._

  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"
  val jettyVersion = "9.2.19.v20160908"

  def specs2Dep(sv: String) =
    "org.specs2" %% "specs2-core" % "3.8.6"

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.4.2" :: Nil

  def integrationTestDeps(sv: String) = (specs2Dep(sv) :: okHttp) map { _ % "test" }

  private[this] val unusedWarnings = (
    "-Ywarn-unused" ::
    "-Ywarn-unused-import" ::
    Nil
  )

  val settings: Seq[Setting[_]] = Defaults.coreDefaultSettings ++ Seq(
    organization := "net.databinder",

    version := "0.9.0-beta1",

    crossScalaVersions := Seq("2.11.8", "2.10.4"),

    scalaVersion := crossScalaVersions.value.head,

    scalacOptions ++=
      Seq("-Xcheckinit", "-encoding", "utf8", "-deprecation", "-unchecked", "-feature"),

    scalacOptions ++= PartialFunction.condOpt(CrossVersion.partialVersion(scalaVersion.value)){
      case Some((2, v)) if v >= 11 => unusedWarnings
    }.toList.flatten,

    javacOptions in Compile ++= Seq("-source", "1.6", "-target", "1.6"),

    incOptions := incOptions.value.withNameHashing(true),

    parallelExecution in Test := false, // :( test servers collide on same port

    homepage := Some(new java.net.URL("http://unfiltered.databinder.net/")),

    publishMavenStyle := true,

    publishTo := Some("releases" at
              "https://oss.sonatype.org/service/local/staging/deploy/maven2"),

    publishArtifact in Test := false,

    licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/MIT")),

    pomExtra := (
      <scm>
        <url>git@github.com:unfiltered/unfiltered.git</url>
        <connection>scm:git:git@github.com:unfiltered/unfiltered.git</connection>
      </scm>
      <developers>
        <developer>
          <id>n8han</id>
          <name>Nathan Hamblen</name>
          <url>http://twitter.com/n8han</url>
        </developer>
        <developer>
          <id>softprops</id>
          <name>Doug Tangren</name>
          <url>http://twitter.com/softprops</url>
        </developer>
      </developers>
    ),

    // this should resolve artifacts recently published to sonatype oss not yet mirrored to maven central
    resolvers += "sonatype releases" at "https://oss.sonatype.org/content/repositories/releases"
  ) ++ Seq(Compile, Test).flatMap(c =>
    scalacOptions in (c, console) --= unusedWarnings
  )
}
