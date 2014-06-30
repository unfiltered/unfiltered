import sbt._
import Keys._
import ls.Plugin.LsKeys

object Shared {

  val servletApiDep = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  val jettyVersion = "7.6.0.v20120127"
  val continuation = "org.eclipse.jetty" % "jetty-continuation" % jettyVersion % "compile"

  def specsDep(sv: String) =
    sv.split("[.-]").toList match {
      case "2" :: "8" :: _ => "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8"
      case "2" :: "9" :: _ => "org.scala-tools.testing" % "specs_2.9.1" % "1.6.9"
      case _ => sys.error("specs not supported for scala version %s" format sv)
    }

  val dispatchVersion = "0.8.8"
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

  val buildSettings = Defaults.defaultSettings ++
    ls.Plugin.lsSettings ++
    Seq(
    organization := "net.databinder",
    version := "0.6.2-SNAPSHOT",
    crossScalaVersions := Seq("2.8.0", "2.8.1", "2.8.2",
                              "2.9.0", "2.9.0-1", "2.9.1", "2.9.1-1", "2.9.2"),
    scalaVersion := "2.8.2",
    credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
    scalacOptions ++= Seq("-Xcheckinit", "-encoding", "utf8", "-deprecation", "-unchecked"),
    parallelExecution in Test := false, // :( test servers collide on same port
    homepage :=
      Some(new java.net.URL("http://unfiltered.databinder.net/")),
    publishMavenStyle := true,
    publishTo := Some("General Sentiment S3 repo" at "s3://s3-us-east-1.amazonaws.com/generalsentiment/release"),
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
      </developers>)

  )

  def srcPathSetting(projectId: String, rootPkg: String) = {
    mappings in (LocalProject(projectId), Compile, packageSrc) ~= {
      defaults: Seq[(File,String)] =>
        defaults.map { case(file, path) =>
          (file, rootPkg + "/" + path)
        }
    }
  }

  private def module(moduleName: String)(
    settings: Seq[Setting[_]],
    projectId: String = "unfiltered-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "unfiltered/" + moduleName.replace("-","/")
  ) = Project(projectId, file(dirName),
              settings = (buildSettings :+
                          srcPathSetting(projectId, srcPath)) ++ settings)

  lazy val unfiltered =
    Project("unfiltered-all", file("."),
            settings = buildSettings ++ Seq(
              name := "Unfiltered",
              LsKeys.skipWrite := true
            )) aggregate(
            library, filters, filtersAsync , uploads, filterUploads,
            nettyUploads, util, jetty,
            jettyAjpProject, netty, nettyServer, json, specHelpers,
            scalaTestHelpers, websockets, oauth,  mac,
            oauth2, agents)

  lazy val library: Project =
    module("unfiltered")(
      dirName = "library",
      projectId = "unfiltered",
      settings = Seq(
        description :=
          "Core library for describing requests and responses",
        unmanagedClasspath in (LocalProject("unfiltered"), Test) <++=
          (fullClasspath in (local("spec"), Compile),
           fullClasspath in (local("filter"), Compile)) map { (s, f) =>
            s ++ f
        },
        libraryDependencies <++= scalaVersion(v => Seq(
          "commons-codec" % "commons-codec" % "1.4",
          Shared.specsDep(v) % "test"
        ) ++ integrationTestDeps(v))
      )
   ) dependsOn(util)

  lazy val filters =
    module("filter")(
      settings = Seq(
        description :=
          "Server binding for Java Servlet filters",
        unmanagedClasspath in (local("filter"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++
          integrationTestDeps(v))
      )) dependsOn(library)

  lazy val filtersAsync =
    module("filter-async")(
      settings = Seq(
        description :=
          "Server binding for Java Servlet 3.0 async filters",
        unmanagedClasspath in (local("filter-async"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion {
          v => Seq(servletApiDep,continuation) ++ integrationTestDeps(v)
        }
      )) dependsOn(filters)

  lazy val agents =
    module("agents")(
      srcPath = "unfiltered/request",
      settings = Seq(
        description :=
          "User-Agent request matchers",
        unmanagedClasspath in (local("agents"), Test) <++=
            (fullClasspath in (local("spec"), Compile),
             fullClasspath in (local("filter"), Compile)) map { (s, f) =>
              s ++ f
        },
        libraryDependencies <++= scalaVersion(v => Seq(servletApiDep) ++
          integrationTestDeps(v))
       )) dependsOn(library)

  lazy val uploads =
    module("uploads")(
      srcPath = "unfiltered/request",
      settings = Seq(
        description :=
          "Generic support for multi-part uploads",
        unmanagedClasspath in (local("uploads"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v => Seq(
          "commons-io" % "commons-io" % "1.4"
        ) ++ integrationTestDeps(v))
       )) dependsOn(library)

  lazy val filterUploads =
    module("filter-uploads")(
      srcPath = "unfiltered/request",
      settings = Seq(
        description :=
          "Support for multi-part uploads for servlet filters",
        unmanagedClasspath in (local("filter-uploads"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v => Seq(
          servletApiDep,
          "commons-fileupload" % "commons-fileupload" % "1.2.1"
        ) ++ integrationTestDeps(v))
      )) dependsOn(uploads, filters)

  lazy val util = module("util")(
    settings = Seq(
      // https://github.com/harrah/xsbt/issues/85#issuecomment-1687483
      unmanagedClasspath in Compile += Attributed.blank(new java.io.File("doesnotexist"))
    ))

  lazy val jetty =
    module("jetty")(
      settings = Seq(
        description :=
          "Jetty server embedding module",
        libraryDependencies := Seq(
          "org.eclipse.jetty" % "jetty-webapp" % jettyVersion
        )
     )) dependsOn(util)

  lazy val jettyAjpProject =
    module("jetty-ajp")(
      settings = Seq(
        description :=
          "Jetty AJP server embedding module",
        libraryDependencies := Seq(
          "org.eclipse.jetty" % "jetty-ajp" % jettyVersion
        )
      )) dependsOn(jetty)

  lazy val nettyServer =
    module("netty-server")(
      srcPath = "unfiltered/netty",
      settings = Seq(
        description :=
          "Netty server embedding module",
        unmanagedClasspath in (local("netty-server"), Test) <++=
            (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <<= scalaVersion(integrationTestDeps _)
       )) dependsOn(netty, util)

  lazy val netty =
    module("netty")(
      settings = Seq(
        description :=
          "Netty server binding module",
        unmanagedClasspath in (local("netty"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v =>
          ("io.netty" % "netty" % "3.4.4.Final" withSources()) +:
          integrationTestDeps(v)
        )
      )) dependsOn(library)

  lazy val specHelpers =
    module("spec")(
      settings = Seq(
        description :=
          "Facilitates testing Unfiltered servers with Specs",
        libraryDependencies <++= scalaVersion { v =>
          specsDep(v) :: dispatchDeps
        }
      )) dependsOn(filters, jetty, nettyServer)

  lazy val scalaTestHelpers =
    module("scalatest")(
      settings = Seq(
        description :=
          "Facilitates testing Unfiltered servers with ScalaTest",
        libraryDependencies <++= scalaVersion(v => Seq(v.split('.').toList match {
        case "2" :: "8" :: _ => "org.scalatest" % "scalatest_2.8.2" % "1.5.1"
        case "2" :: "9" :: "1" :: _ => "org.scalatest" % "scalatest_2.9.1" % "1.6.1"
        case "2" :: "9" :: _ => "org.scalatest" % "scalatest_2.9.0-1" % "1.6.1"
        case _ => sys.error("ScalaTest not supported for scala version %s" format v)
    }) ++ dispatchDeps)
      )) dependsOn(jetty, nettyServer)

  lazy val json =
    module("json")(
      srcPath = "unfiltered",
      settings = Seq(
        description :=
          "Json requset matchers and response functions",
        unmanagedClasspath in (local("json"), Test) <++=
          (fullClasspath in (local("spec"), Compile),
           fullClasspath in (local("filter"), Compile)) map { (s, f) =>
             s ++ f
          },
        libraryDependencies <++= scalaVersion( sv =>
          Seq(sv.split("[.-]").toList match {
            case "2" :: "9" :: _ =>
              "net.liftweb" % "lift-json_2.9.1" % "2.4"
            case _ => "net.liftweb" %% "lift-json" % "2.4"
          }) ++ integrationTestDeps(sv))
      )) dependsOn(library)

  lazy val websockets =
    module("netty-websockets")(
      settings = Seq(
        description :=
          "WebSockets plan support using Netty",
        unmanagedClasspath in (local("netty-websockets"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(integrationTestDeps _)
      )) dependsOn(nettyServer)

  lazy val oauth =
    module("oauth")(
      settings = Seq(
        description :=
          "OAuth plans for servlet filters",
        unmanagedClasspath in (local("oauth"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v =>
          Seq(dispatchOAuthDep) ++
          integrationTestDeps(v))
      )) dependsOn(jetty, filters)

  lazy val mac =
    module("mac")(
      settings = Seq(
        name := "Unfiltered MAC",
        unmanagedClasspath in (local("mac"), Test) <++=
          (fullClasspath in (local("spec"), Compile),
          fullClasspath in (local("filter"), Compile)) map { (s, f) =>
            s ++ f
          },
        libraryDependencies <++= scalaVersion(v =>
          Seq(dispatchOAuthDep) ++ integrationTestDeps(v))
      )) dependsOn(library)

  lazy val oauth2 =
    module("oauth2")(
      settings = Seq(
        name := "Unfiltered OAuth2",
        unmanagedClasspath in (local("oauth2"), Test) <++=
          (fullClasspath in (local("spec"), Compile),
          fullClasspath in (local("filter"), Compile)) map { (s, f) =>
            s ++ f
          },
        libraryDependencies <++= scalaVersion(v =>
          Seq(dispatchOAuthDep) ++ integrationTestDeps(v))
      )) dependsOn(jetty, filters, mac)
      
  lazy val nettyUploads =
    module("netty-uploads")(
      settings = Seq(
        description :=
          "Uploads plan support using Netty",
        unmanagedClasspath in (local("netty-uploads"), Test) <++=
          (fullClasspath in (local("spec"), Compile)),
        libraryDependencies <++= scalaVersion(v =>
          integrationTestDeps(v)
        ),
        scalacOptions ++= Seq("-deprecation", "-unchecked")
      )) dependsOn(nettyServer, uploads)
}
