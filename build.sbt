import Common._
import Unfiltered._
import Dependencies._
import ReleaseTransformations._

Common.settings

enablePlugins(ScalaUnidocPlugin)

// unidoc publish settings
name := "unfiltered-all"
artifacts := Classpaths.artifactDefs(Seq(Compile / packageDoc, Compile / makePom)).value
packagedArtifacts := Classpaths.packaged(Seq(Compile / packageDoc, Compile / makePom)).value
Defaults.packageTaskSettings(
  Compile / packageDoc,
  (Compile / unidoc).map { _.flatMap(Path.allSubpaths) }
)

releaseCrossBuild := true

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  runTest,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  releaseStepCommandAndRemaining("+publishSigned"),
  releaseStepCommandAndRemaining("sonatypeBundleRelease"),
  setNextVersion,
  commitNextVersion,
  pushChanges,
)

val specs2ProjectId = "specs2"
val scalatestProjectId = "scalatest"
val filterProjectId = "filter"

// avoid cyclic error
def dependsOnInTest(id: String) =
  (Test / unmanagedClasspath) ++= (local(id) / Compile / fullClasspath).value

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
    "commons-codec" % "commons-codec" % commonsCodecVersion,
    specs2Dep.value % "test",
    "org.scalatest" %% "scalatest-wordspec" % scalatestVersion % "test",
    "org.scalatest" %% "scalatest-shouldmatchers" % scalatestVersion % "test",
    "org.scalatest" %% "scalatest-propspec" % scalatestVersion % "test",
    "org.scalatestplus" %% "scalacheck-1-17" % scalatestScalacheckVersion % "test",
    "org.scala-lang.modules" %% "scala-xml" % scalaXmlVersion,
  ),
).dependsOn(util)

lazy val directives = module("directives")()
  .settings(
    description := "monadic api for unfiltered",
    Test / sources := {
      if (scalaBinaryVersion.value == "3") {
        // TODO
        val exclude = Set(
          "DirectivesSpec.scala"
        )
        (Test / sources).value.filterNot(f => exclude(f.getName))
      } else {
        (Test / sources).value
      }
    }
  )
  .dependsOn(library, specs2 % "test")

lazy val filters = module(filterProjectId)()
  .settings(
    description := "Server binding for Java Servlet filters",
    libraryDependencies += servletApiDep,
    dependsOnSpecs2InTest
  )
  .dependsOn(library)

lazy val filtersAsync = module("filter-async")()
  .settings(
    description := "Server binding for Java Servlet async filters",
    libraryDependencies += servletApiDep
  )
  .dependsOn(filters, specs2 % "test")

lazy val agents = module("agents")(
  srcPath = "unfiltered/request"
).settings(
  description := "User-Agent request matchers",
  libraryDependencies ++= Seq(
    "org.scalatest" %% "scalatest-wordspec" % scalatestVersion % "test",
    "org.scalatest" %% "scalatest-shouldmatchers" % scalatestVersion % "test",
  ),
  libraryDependencies ++= Seq(servletApiDep) ++ integrationTestDeps.value
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
    "commons-io" % "commons-io" % commonsIoVersion
  ) ++ integrationTestDeps.value
).dependsOn(library, specs2 % "test")

lazy val filterUploads = module("filter-uploads")(
  srcPath = "unfiltered/request"
).settings(
  description := "Support for multi-part uploads for servlet filters",
  libraryDependencies ++= Seq(
    servletApiDep,
    "commons-fileupload" % "commons-fileupload" % commonsFileUploadVersion
  ) ++ integrationTestDeps.value
).dependsOn(uploads, filters, specs2 % "test")

lazy val util = module("util")().settings(
  libraryDependencies += specs2Dep.value % "test"
)

lazy val jetty = module("jetty")()
  .settings(
    description := "Jetty server embedding module",
    libraryDependencies := Seq(
      "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
    )
  )
  .dependsOn(util)

lazy val nettyServer = module("netty-server")(
  srcPath = "unfiltered/netty"
).settings(
  description := "Netty server embedding module",
  dependsOnSpecs2InTest,
  libraryDependencies += "jakarta.activation" % "jakarta.activation-api" % activationVersion,
  libraryDependencies += "org.eclipse.angus" % "angus-core" % "2.0.3" % Test,
  libraryDependencies ++= integrationTestDeps.value
).dependsOn(netty, util)

lazy val netty = module("netty")()
  .settings(
    description := "Netty server binding module",
    dependsOnSpecs2InTest,
    libraryDependencies ++= {
      ("io.netty" % "netty-codec-http" % nettyVersion) +:
        ("io.netty" % "netty-handler" % nettyVersion) +:
        ("io.netty" % "netty-transport-native-epoll" % nettyVersion classifier "linux-x86_64") +:
        ("io.netty" % "netty-transport-native-epoll" % nettyVersion classifier "linux-aarch_64") +:
        ("io.netty" % "netty-transport-native-kqueue" % nettyVersion classifier "osx-x86_64") +:
        ("io.netty" % "netty-transport-native-kqueue" % nettyVersion classifier "osx-aarch_64") +:
        integrationTestDeps.value
    }
  )
  .dependsOn(library)

lazy val specs2: Project = module(specs2ProjectId)()
  .settings(
    description := "Facilitates testing Unfiltered servers with Specs2",
    libraryDependencies ++= {
      specs2Dep.value :: okHttp
    }
  )
  .dependsOn(filters, jetty, nettyServer)

lazy val scalatest = module(scalatestProjectId)()
  .settings(
    description := "Facilitates testing Unfiltered servers with ScalaTest",
    libraryDependencies ++= {
      okHttp :+
        ("org.scalatest" %% "scalatest-core" % scalatestVersion)
    }
  )
  .dependsOn(filters, jetty, nettyServer)

lazy val json4s = module("json4s")(
  srcPath = "unfiltered"
).settings(
  description := "Json4s request matchers and response functions",
  libraryDependencies ++= {
    Seq("org.json4s" %% "json4s-native-core" % json4sVersion) ++ integrationTestDeps.value
  }
).dependsOn(library, filters % "test", specs2 % "test")

lazy val websockets = module("netty-websockets")()
  .settings(
    description := "WebSockets plan support using Netty",
    libraryDependencies ++= integrationTestDeps.value,
    libraryDependencies += "com.ning" % "async-http-client" % asyncHttpClientVersion % "test"
  )
  .dependsOn(nettyServer, specs2 % "test")

lazy val nettyUploads = module("netty-uploads")()
  .settings(
    description := "Uploads plan support using Netty",
    Test / sources := {
      if (scalaBinaryVersion.value == "3") {
        // TODO
        val exclude = Set(
          "MixedPlanSpec.scala"
        )
        (Test / sources).value.filterNot(f => exclude(f.getName))
      } else {
        (Test / sources).value
      }
    },
    libraryDependencies ++= integrationTestDeps.value,
    Test / parallelExecution := false
  )
  .dependsOn(nettyServer, uploads, specs2 % "test")
