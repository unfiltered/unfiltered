import Common._
import Unfiltered._

name := "Unfiltered"

Common.settings

val specs2ProjectId = "specs2"
val scalatestProjectId = "scalatest"
val filterProjectId = "filter"

// avoid cyclic error
def dependsOnInTest(id: String) =
  unmanagedClasspath in Test ++= (fullClasspath in (local(id), Compile)).value

val dependsOnSpecs2InTest = dependsOnInTest(specs2ProjectId)

lazy val library: Project = module("unfiltered")(
  dirName = "library",
  projectId = "unfiltered"
).settings(
  description := "Core library for describing requests and responses",
  dependsOnSpecs2InTest,
  dependsOnInTest(scalatestProjectId),
  dependsOnInTest(filterProjectId),
  libraryDependencies ++= Seq(
    "commons-codec" % "commons-codec" % "1.10",
    specs2Dep(scalaVersion.value) % "test",
    "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
    "joda-time" % "joda-time" % "2.9.6" % "test",
    "org.joda" % "joda-convert" % "1.8.1" % "test"
  ),
  libraryDependencies ++= {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 11 =>
        Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.6")
      case _ =>
        Nil
    }
  }
).dependsOn(util)

lazy val directives = module("directives")().settings(
  description := "monadic api for unfiltered"
).dependsOn(library, specs2 % "test")

lazy val filters = module(filterProjectId)().settings(
  description := "Server binding for Java Servlet filters",
  libraryDependencies += servletApiDep,
  dependsOnSpecs2InTest
).dependsOn(library)

lazy val filtersAsync = module("filter-async")().settings(
  description := "Server binding for Java Servlet 3.0 async filters",
  libraryDependencies += servletApiDep
).dependsOn(filters, specs2 % "test")

lazy val agents = module("agents")(
  srcPath = "unfiltered/request"
).settings(
  description := "User-Agent request matchers",
  libraryDependencies ++= Seq(servletApiDep) ++ integrationTestDeps(scalaVersion.value)
).dependsOn(
  library,
  scalatest % "test",
  filters % "test"
)

lazy val uploads = module("uploads")(
  srcPath = "unfiltered/request"
).settings(
  description := "Generic support for multi-part uploads",
  libraryDependencies ++= Seq(
    "commons-io" % "commons-io" % "2.5"
  ) ++ integrationTestDeps(scalaVersion.value)
).dependsOn(library, specs2 % "test")

lazy val filterUploads = module("filter-uploads")(
  srcPath = "unfiltered/request"
).settings(
  description := "Support for multi-part uploads for servlet filters",
  libraryDependencies ++= Seq(
    servletApiDep,
    "commons-fileupload" % "commons-fileupload" % "1.3.2"
  ) ++ integrationTestDeps(scalaVersion.value)
).dependsOn(uploads, filters, specs2 % "test")

lazy val util = module("util")()

lazy val jetty = module("jetty")().settings(
  description := "Jetty server embedding module",
  libraryDependencies := Seq(
    "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
  )
).dependsOn(util)

lazy val nettyServer = module("netty-server")(
  srcPath = "unfiltered/netty"
).settings(
  description := "Netty server embedding module",
  dependsOnSpecs2InTest,
  libraryDependencies ++= integrationTestDeps(scalaVersion.value)
).dependsOn(netty, util)

val nettyVersion = "4.1.6.Final"

lazy val netty = module("netty")().settings(
  description := "Netty server binding module",
  dependsOnSpecs2InTest,
  libraryDependencies ++= {
    ("io.netty" % "netty-codec-http" % nettyVersion) +:
    ("io.netty" % "netty-handler" % nettyVersion) +:
    integrationTestDeps(scalaVersion.value)
  }
).dependsOn(library)

lazy val specs2: Project = module(specs2ProjectId)().settings(
  description := "Facilitates testing Unfiltered servers with Specs2",
  libraryDependencies ++= {
    specs2Dep(scalaVersion.value) :: okHttp
  }
).dependsOn(filters, jetty, nettyServer)

lazy val scalatest = module(scalatestProjectId)().settings(
  description := "Facilitates testing Unfiltered servers with ScalaTest",
  libraryDependencies ++= okHttp :+ "org.scalatest" %% "scalatest" % "3.0.1"
).dependsOn(filters, jetty, nettyServer)

lazy val json4s = module("json4s")(
  srcPath = "unfiltered"
).settings(
  description := "Json4s request matchers and response functions",
  libraryDependencies ++= {
    Seq("org.json4s" %% "json4s-native" % "3.5.0") ++ integrationTestDeps(scalaVersion.value)
  }
).dependsOn(library, filters % "test", specs2 % "test")

lazy val websockets = module("netty-websockets")().settings(
  description := "WebSockets plan support using Netty",
  libraryDependencies ++= integrationTestDeps(scalaVersion.value),
  libraryDependencies += "com.ning" % "async-http-client" % "1.8.17" % "test"
).dependsOn(nettyServer, specs2 % "test")

lazy val oauth = module("oauth")().settings(
  description := "OAuth plans for servlet filters",
  libraryDependencies += "com.github.scribejava" % "scribejava-core" % "3.3.0" % "test",
  libraryDependencies ++= integrationTestDeps(scalaVersion.value)
).dependsOn(jetty, filters, directives, specs2 % "test")

lazy val mac = module("mac")().settings(
  description := "MAC utilities for oauth2 module"
).dependsOn(library, specs2 % "test")

lazy val oauth2 = module("oauth2")().settings(
  description := "OAuth2 module for unfiltered"
).dependsOn(jetty, filters, mac, directives, specs2 % "test")

lazy val nettyUploads = module("netty-uploads")().settings(
  description := "Uploads plan support using Netty",
  libraryDependencies ++= integrationTestDeps(scalaVersion.value),
  parallelExecution in Test := false
).dependsOn(nettyServer, uploads, specs2 % "test")
