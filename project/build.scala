import sbt._
import Keys._

object Shared {

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

object Unfiltered extends Build {
  import Shared._
  import posterous.Publish.{previewNotes, publishNotes, posterousCheck,
                            posterousRequiredInputs, posterousDupCheck}

  def id(name: String) = "unfiltered-%s" format name

  def local(name: String) = LocalProject(id(name))

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "net.databinder",
    name := "Unfiltered",
    version := "0.4.0",
    crossScalaVersions := Seq("2.8.0", "2.8.1", "2.9.0", "2.9.0-1"),
    scalaVersion := "2.8.1",
    publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    scalacOptions ++= Seq("-Xcheckinit", "-encoding", "utf8"),
    parallelExecution in Test := false // :( test servers collide on same port
  )

  lazy val unfiltered =
    Project("unfiltered-all", file("."),
            settings = buildSettings ++ Seq(
              aggregate in previewNotes := false,
              aggregate in publishNotes := false,
              aggregate in posterousCheck := false,
              aggregate in posterousRequiredInputs := false,
              aggregate in posterousDupCheck := false
            )) aggregate(
            library, filters, uploads, util, jetty, jettyAjpProject,
            netty, nettyServer, json, specHelpers, scalaTestHelpers,
            scalate, websockets, oauth)

  lazy val library: Project =
    Project("unfiltered", file("library"),
            settings = buildSettings ++ Seq(
              name := "unfiltered",
              unmanagedClasspath in (LocalProject("unfiltered"), Test) <++=
                (fullClasspath in (local("spec"), Compile),
                 fullClasspath in (local("filter"), Compile)) map { (s, f) =>
                  s ++ f
              },
              libraryDependencies <++= scalaVersion(v => Seq(
                "commons-codec" % "commons-codec" % "1.4",
                Shared.specsDep(v) % "test"
             ) ++ integrationTestDeps(v))
            )) dependsOn(util)

  lazy val filters =
    Project(id("filter"), file("filter"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Filter",
            unmanagedClasspath in (local("filter"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++
              integrationTestDeps(v))
          )) dependsOn(library)

  lazy val uploads =
    Project(id("uploads"), file("uploads"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Uploads",
            unmanagedClasspath in (local("uploads"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v => Seq(
              servletApiDep,
              "commons-io" % "commons-io" % "1.4",
              "commons-fileupload" % "commons-fileupload" % "1.2.1"
            ) ++ integrationTestDeps(v)))) dependsOn(filters)

  lazy val util =
    Project(id("utils"), file("util"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Utils"))

  lazy val jetty =
    Project(id("jetty"), file("jetty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Jetty",
              libraryDependencies := Seq(
                "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
              ))) dependsOn(util)

  lazy val jettyAjpProject =
    Project(id("jetty-ajp"), file("jetty-ajp"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Jetty AJP",
            libraryDependencies := Seq(
              "org.eclipse.jetty" % "jetty-ajp" % jettyVersion
            ))) dependsOn(jetty)

  lazy val nettyServer =
    Project(id("netty-server"), file("netty-server"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Netty Server",
            libraryDependencies := Seq(
              "org.jboss.netty" % "netty" % "3.2.4.Final" withSources()
           ))) dependsOn(util)

  lazy val netty =
    Project(id("netty"), file("netty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Netty",
              unmanagedClasspath in (local("netty"), Test) <++=
                (fullClasspath in (local("spec"), Compile)).identity,
              libraryDependencies <++= scalaVersion(integrationTestDeps _)
            )) dependsOn(nettyServer, library)

  lazy val specHelpers =
    Project(id("spec"), file("spec"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Spec",
              libraryDependencies <++= scalaVersion(v => Seq(specsDep(v), dispatchDep(v)))
            )) dependsOn(jetty, netty)

  lazy val scalaTestHelpers =
    Project(id("scalatest"), file("scalatest"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Scalatest",
            libraryDependencies <++= scalaVersion(v =>
              Seq("org.scalatest" % "scalatest" % "1.3", dispatchDep(v)))
          )) dependsOn(jetty, netty)

  lazy val json =
    Project(id("json"), file("json"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Json",
            unmanagedClasspath in (local("json"), Test) <++=
              (fullClasspath in (local("spec"), Compile),
               fullClasspath in (local("filter"), Compile)) map { (s, f) =>
                 s ++ f
              },
            libraryDependencies <++= scalaVersion(v => Seq(
              if (v.startsWith("2.8")) "net.liftweb" %% "lift-json" % "2.3"
              else "net.liftweb" %% "lift-json" % "2.4-M2") ++ integrationTestDeps(v))
          )) dependsOn(library)

  lazy val scalate =
    Project(id("scalate"), file("scalate"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Scalate",
              libraryDependencies <++= scalaVersion { v =>
                val scalateVersion = v match {
                  case "2.8.0" | "2.8.1" => "1.4.1"
                  case _ => "1.5.0"
                }
                Seq(
                  "org.fusesource.scalate" % "scalate-core" % scalateVersion,
                  "org.fusesource.scalate" % "scalate-util" % scalateVersion % "test",
                  "org.scala-lang" % "scala-compiler" % v % "test",
                  "org.mockito" % "mockito-core" % "1.8.5" % "test"
                ) ++ integrationTestDeps(v) } )) dependsOn(library)

  lazy val websockets =
    Project(id("websockets"), file("websockets"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Websockets",
            unmanagedClasspath in (local("websockets"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(integrationTestDeps _)
          )) dependsOn(netty)

  lazy val oauth =
    Project(id("oauth"), file("oauth"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered OAuth",
            unmanagedClasspath in (local("oauth"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v => Seq(dispatchOAuthDep(v)) ++
              integrationTestDeps(v))
          )) dependsOn(jetty, filters)
}
