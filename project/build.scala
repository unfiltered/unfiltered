import sbt._
import Keys._

object Shared {

  val servletApiDep = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  val jettyVersion = "7.2.2.v20101205"
  val continuation = "org.eclipse.jetty" % "jetty-continuation" % "7.5.1.v20110908" % "compile"

  def specsDep(sv: String) =
    sv.split('.').toList match {
      case "2" :: "8" :: _ => "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8"
      case "2" :: "9" :: "1" :: _ => "org.scala-tools.testing" % "specs_2.9.1" % "1.6.9"
      case "2" :: "9" :: _ => "org.scala-tools.testing" %% "specs" % "1.6.8"
      case _ => error("specs not supported for scala version %s" format sv)
    }

  val dispatchVersion = "0.8.5"
  def dispatchDeps =
    "net.databinder" %% "dispatch-mime" % dispatchVersion ::
    "net.databinder" %% "dispatch-http" % dispatchVersion :: Nil

  def dispatchOAuthDep =
    "net.databinder" %% "dispatch-oauth" % dispatchVersion

  def integrationTestDeps(sv: String) = (specsDep(sv) :: dispatchDeps) map { _ % "test" }
}

object Unfiltered extends Build {
  import Shared._

  def id(name: String) = "unfiltered-%s" format name

  def local(name: String) = LocalProject(id(name))

  val buildSettings = Defaults.defaultSettings ++ Seq(
    organization := "net.databinder",
    name := "Unfiltered",
    version := "0.5.1-SNAPSHOT",
    crossScalaVersions := Seq("2.8.0", "2.8.1", "2.9.0", "2.9.0-1", "2.9.1"),
    scalaVersion := "2.9.1",
    publishTo := Some("Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"),
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    scalacOptions ++= Seq("-Xcheckinit", "-encoding", "utf8"),
    parallelExecution in Test := false // :( test servers collide on same port
  )

  def srcPath(projectId: String, rootPkg: String) = {
    mappings in (LocalProject(projectId), Compile, packageSrc) ~= { defaults: Seq[(File,String)] =>
      defaults.map { case(file, path) =>
        (file, rootPkg + "/" + path)
      }
    }
  }

  lazy val unfiltered =
    Project("unfiltered-all", file("."),
            settings = buildSettings) aggregate(
            library, filters, filtersAsync , uploads, util, jetty, jettyAjpProject,
            netty, nettyServer, json, specHelpers, scalaTestHelpers,
            scalate, websockets, oauth, agents)

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
              ) ++ integrationTestDeps(v)),
              srcPath("unfiltered", "unfiltered")
           )) dependsOn(util)

  lazy val filters =
    Project(id("filter"), file("filter"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Filter",
            unmanagedClasspath in (local("filter"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++
              integrationTestDeps(v)),
            srcPath(id("filter"), "unfiltered/filter")
          )) dependsOn(library)

  lazy val filtersAsync =
    Project(id("filter-async"), file("filter-async"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Filter Async",
            unmanagedClasspath in (local("filter-async"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v => Seq(servletApiDep,continuation) ++
              integrationTestDeps(v))
          )) dependsOn(filters)

  lazy val agents =
    Project(id("agents"), file("agents"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Agents",
            unmanagedClasspath in (local("agents"), Test) <++=
                (fullClasspath in (local("spec"), Compile),
                 fullClasspath in (local("filter"), Compile)) map { (s, f) =>
                  s ++ f
            },
            libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++
              integrationTestDeps(v)),
            srcPath(id("agents"), "unfiltered/request")
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
            ) ++ integrationTestDeps(v)),
            srcPath(id("uploads"), "unfiltered/request")
           )) dependsOn(filters)

  lazy val util =
    Project(id("utils"), file("util"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Utils",
            // https://github.com/harrah/xsbt/issues/76
            publishArtifact in packageDoc := false,
            srcPath(id("utils"), "unfiltered/util")))

  lazy val jetty =
    Project(id("jetty"), file("jetty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Jetty",
              libraryDependencies := Seq(
                "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
              ),
              srcPath(id("jetty"), "unfiltered/jetty")
           )) dependsOn(util)

  lazy val jettyAjpProject =
    Project(id("jetty-ajp"), file("jetty-ajp"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Jetty AJP",
            libraryDependencies := Seq(
              "org.eclipse.jetty" % "jetty-ajp" % jettyVersion
            ),
            srcPath(id("jetty-ajp"), "unfiltered/jetty/ajp")
           )) dependsOn(jetty)

  lazy val nettyServer =
    Project(id("netty-server"), file("netty-server"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Netty Server",
            unmanagedClasspath in (local("netty-server"), Test) <++=
                (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <<= scalaVersion(integrationTestDeps _),
            srcPath(id("netty-server"), "unfiltered/netty")
           )) dependsOn(netty, util)

  lazy val netty =
    Project(id("netty"), file("netty"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Netty",
              unmanagedClasspath in (local("netty"), Test) <++=
                (fullClasspath in (local("spec"), Compile)).identity,
              libraryDependencies <++= scalaVersion(v =>
                ("org.jboss.netty" % "netty" % "3.2.5.Final" withSources()) +:
                integrationTestDeps(v)
              ),
              srcPath(id("netty"), "unfiltered/netty")
            )) dependsOn(library)

  lazy val specHelpers =
    Project(id("spec"), file("spec"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Spec",
              libraryDependencies <++= scalaVersion { v =>
                specsDep(v) :: dispatchDeps
              },
              srcPath(id("spec"), "unfiltered/spec")
            )) dependsOn(filters, jetty, nettyServer)

  lazy val scalaTestHelpers =
    Project(id("scalatest"), file("scalatest"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Scalatest",
            libraryDependencies ++=
              ("org.scalatest" % "scalatest" % "1.3") :: dispatchDeps,
            srcPath(id("scalatest"), "unfiltered/scalatest")
          )) dependsOn(jetty, nettyServer)

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
              v.split('.').toList match {
                case "2" :: "8" :: _ => "net.liftweb" %% "lift-json" % "2.3"
                case "2" :: "9" :: "1" :: _ =>  "net.liftweb" % "lift-json_2.9.1" % "2.4-M4"
                case _ => "net.liftweb" %% "lift-json" % "2.4-M3"
              }) ++ integrationTestDeps(v))
          )) dependsOn(library)

  lazy val scalate =
    Project(id("scalate"), file("scalate"),
            settings = buildSettings ++ Seq(
              name := "Unfiltered Scalate",
              libraryDependencies <++= scalaVersion { v =>
                val scalateVersion = v match {
                  case "2.8.0" | "2.8.1" => "1.4.1"
                  case _ => "1.5.2"
                }
                Seq(
                  "org.fusesource.scalate" % "scalate-core" % scalateVersion,
                  "org.fusesource.scalate" % "scalate-util" % scalateVersion % "test",
                  "org.scala-lang" % "scala-compiler" % v % "test",
                  "org.mockito" % "mockito-core" % "1.8.5" % "test"
                ) ++ integrationTestDeps(v) },
                srcPath(id("scalate"), "unfiltered/scalate") )) dependsOn(library)

  lazy val websockets =
    Project(id("websockets"), file("websockets"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered Websockets",
            unmanagedClasspath in (local("websockets"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(integrationTestDeps _),
            srcPath(id("websockets"), "unfiltered/netty/websockets")
          )) dependsOn(nettyServer)

  lazy val oauth =
    Project(id("oauth"), file("oauth"),
          settings = buildSettings ++ Seq(
            name := "Unfiltered OAuth",
            unmanagedClasspath in (local("oauth"), Test) <++=
              (fullClasspath in (local("spec"), Compile)).identity,
            libraryDependencies <++= scalaVersion(v =>
              Seq(dispatchOAuthDep) ++
              integrationTestDeps(v)),
            srcPath(id("oauth"), "unfiltered/oauth")
          )) dependsOn(jetty, filters)
}
