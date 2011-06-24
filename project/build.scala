import sbt._
import Keys._

object Shared {
  val buildVersion = "0.4.0-SNAPSHOT"

  val servletApiDep = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  val jettyVersion = "7.2.2.v20101205"

  def specsDep(sv: String) =
    sv.substring(0,3) match {
      case "2.8" => "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8"
      case "2.9" => "org.scala-tools.testing" %% "specs" % "1.6.8"
      case _ => error("unsupported")
    }

  def dispatchDep(sv: String) = if(sv startsWith "2.8.1")
      "net.databinder" % "dispatch-mime_2.8.0" % "0.7.8"
    else
      "net.databinder" %% "dispatch-mime" % "0.7.8"

  def dispatchOAuthDep(sv: String) = if(sv startsWith "2.8.1")
      "net.databinder" % "dispatch-oauth_2.8.0" % "0.7.8"
    else
      "net.databinder" %% "dispatch-oauth" % "0.7.8"

  def integrationTestDeps(sv: String) = Seq(specsDep(sv) % "test", dispatchDep(sv) % "test")
}

object Shell {
  object devnull extends ProcessLogger {
    def info(m: => String) {}
    def error(m: => String) {}
    def buffer[T](f: => T): T = f
  }

  val CurrentBranch = """\*\s+([^\s]+)""".r

  def gitBranches = ("git branch --no-color" lines_! devnull mkString)

 // todo: n8. is git-branch overkill? may be better suited for local plugin than default
  def ps1(sv:String) = (s: State) => "%s@%s scala:%s (%s) > " format(
    Project.extract(s).currentProject.id,
    Shared.buildVersion,
    sv,
    CurrentBranch findFirstMatchIn gitBranches map (_ group(1)) getOrElse "-"
  )
}

// todo: integration testing settings :: posterous :: sxr :: Nil
object Unfiltered extends Build {
  import Shared._

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "net.databinder",
    name := "Unfiltered Web Toolkit",
    version := buildVersion,
    crossScalaVersions := Seq("2.8.0", "2.8.1", "2.9.0", "2.9.0-1"),
    scalaVersion := "2.8.1",
    publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    scalacOptions ++= Seq("-Xcheckinit", "-encoding", "utf8"),
    shellPrompt <<= scalaVersion(Shell.ps1 _)
  )

  lazy val unfiltered =
    Project("Unfiltered", file("."),
            settings = buildSettings) aggregate(
            library, filters, uploads, util, jetty, jettyAjpProject,
            netty, nettyServer, json, specHelpers, scalaTestHelpers,
            scalate, websockets, oauth)

  lazy val library: Project =
    Project("library", file("library"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered",
              libraryDependencies <++= scalaVersion(v => Seq(
                "commons-codec" % "commons-codec" % "1.4",
                Shared.specsDep(v) % "test"
             ) ++ integrationTestDeps(v))
            )) dependsOn(util)

  lazy val filters =
    Project("filter", file("filter"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Filter",
            libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++ integrationTestDeps(v))
          )) dependsOn(library)

  lazy val uploads =
    Project("uploads", file("uploads"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Uploads",
            libraryDependencies <++= scalaVersion(v => Seq(
              servletApiDep,
              "commons-io" % "commons-io" % "1.4",
              "commons-fileupload" % "commons-fileupload" % "1.2.1"
            ) ++ integrationTestDeps(v)))) dependsOn(filters)

  lazy val util =
    Project("util", file("util"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Utils"))

  lazy val jetty =
    Project("jetty", file("jetty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Jetty",
              libraryDependencies := Seq(
                "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
              ))) dependsOn(util)

  lazy val jettyAjpProject =
    Project("jetty-ajp", file("jetty-ajp"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Jetty AJP",
            libraryDependencies := Seq(
              "org.eclipse.jetty" % "jetty-ajp" % jettyVersion
            ))) dependsOn(jetty)

  lazy val nettyServer =
    Project("netty-server", file("netty-server"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Netty Server",
            libraryDependencies := Seq(
              "org.jboss.netty" % "netty" % "3.2.4.Final" withSources()
           ))) dependsOn(util)

  lazy val netty =
    Project("netty", file("netty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Netty",
              libraryDependencies <++= scalaVersion(integrationTestDeps _)
            )) dependsOn(nettyServer, library)

  lazy val specHelpers =
    Project("spec", file("spec"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Spec",
              libraryDependencies <++= scalaVersion(v => Seq(specsDep(v), dispatchDep(v)))
            )) dependsOn(jetty, netty)

  lazy val scalaTestHelpers =
    Project("scalatest", file("scalatest"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Scalatest",
            libraryDependencies <++= scalaVersion(v => Seq("org.scalatest" % "scalatest" % "1.3", dispatchDep(v)))
          )) dependsOn(jetty, netty)

  lazy val json =
    Project("json", file("json"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Json",
            libraryDependencies <++= scalaVersion(v => Seq(
              if (v.startsWith("2.8")) "net.liftweb" %% "lift-json" % "2.3"
              else "net.liftweb" %% "lift-json" % "2.4-M2") ++ integrationTestDeps(v))
          )) dependsOn(library)

  lazy val scalate =
    Project("scalate", file("scalate"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Scalate",
              resolvers += ScalaToolsSnapshots,
              libraryDependencies <++= scalaVersion(v => Seq(
                "org.fusesource.scalate" % "scalate-core" % "1.4.1",
                "org.fusesource.scalate" % "scalate-util" % "1.4.1" % "test",
                "org.scala-lang" % "scala-compiler" % v % "test",
                "org.mockito" % "mockito-core" % "1.8.5" % "test"
              ) ++ integrationTestDeps(v)))) dependsOn(library)

  lazy val websockets =
    Project("websockets", file("websockets"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Websockets",
            libraryDependencies <++= scalaVersion(integrationTestDeps _)
          )) dependsOn(netty)

  lazy val oauth =
    Project("oauth", file("oauth"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered OAuth",
            libraryDependencies <++= scalaVersion(v => Seq(dispatchOAuthDep(v)) ++ integrationTestDeps(v))
          )) dependsOn(jetty, filters)
}
