import sbt._
import Keys._
import ls.Plugin.LsKeys

object Shared {

  val servletApiDep = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  val jettyVersion = "7.6.0.v20120127"

  def specsDep(sv: String) =
    sv.split("[.-]").toList match {
      case "2" :: "8" :: _ => "org.scala-tools.testing" % "specs_2.8.1" % "1.6.8"
      case "2" :: "9" :: "0" :: "1" :: _ => "org.scala-tools.testing" %% "specs" % "1.6.8"
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
  import java.lang.{ Boolean => JBoolean }

  def id(name: String) = "unfiltered-%s" format name

  def local(name: String) = LocalProject(id(name))

  def srcPathSetting(projectId: String, rootPkg: String) =
    mappings in (LocalProject(projectId), Compile, packageSrc) ~= {
      defaults: Seq[(File,String)] =>
        defaults.map { case(file, path) =>
          (file, rootPkg + "/" + path)
        }
    }

  private def ciSettings: Seq[Project.Setting[_]] = {
    if (JBoolean.parseBoolean(
      sys.env.getOrElse("TRAVIS", "false"))) Seq(
      logLevel in Global := Level.Warn,
      logLevel in Compile := Level.Warn,
      logLevel in Test := Level.Info
    ) else Seq.empty[Project.Setting[_]]
  }
  private def module(moduleName: String)(
    projectId: String = "unfiltered-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "unfiltered/" + moduleName.replace("-","/")
  ) = Project(projectId, file(dirName),
              settings = (Defaults.defaultSettings ++
                          ls.Plugin.lsSettings ++
                          ciSettings ++
                          srcPathSetting(projectId, srcPath)
            )).delegateTo(setup)

  /** Defines common settings for all projects */
  lazy val setup = Project("setup", file("setup"))

  lazy val unfiltered =
    Project("unfiltered-all", file(".")).delegateTo(setup).aggregate(
            library, filters, filtersAsync , uploads, filterUploads,
            //nettyUploads, 
	    util, jetty,
            jettyAjpProject, netty, nettyServer, json, specHelpers,
            scalaTestHelpers, websockets, oauth,  mac,
            oauth2, agents)

  lazy val library: Project =
    module("unfiltered")(
      dirName = "library",
      projectId = "unfiltered"
   ).dependsOn(util)

  lazy val filters = module("filter")().dependsOn(library)

  lazy val filtersAsync = module("filter-async")().dependsOn(filters)

  lazy val agents =
    module("agents")(
      srcPath = "unfiltered/request"
    ).dependsOn(library)

  lazy val uploads =
    module("uploads")(
      srcPath = "unfiltered/request"
    ).dependsOn(library)

  lazy val filterUploads =
    module("filter-uploads")(
      srcPath = "unfiltered/request"
    ).dependsOn(uploads, filters)

  lazy val util = module("util")()

  lazy val jetty = module("jetty")().dependsOn(util)

  lazy val jettyAjpProject = module("jetty-ajp")().dependsOn(jetty)

  lazy val nettyServer =
    module("netty-server")(
      srcPath = "unfiltered/netty"
    ).dependsOn(netty, util)

  lazy val netty = module("netty")().dependsOn(library)

  lazy val specHelpers =
    module("spec")().dependsOn(filters, jetty, nettyServer)

  lazy val scalaTestHelpers =
    module("scalatest")().dependsOn(jetty, nettyServer)

  lazy val json =
    module("json")(
      srcPath = "unfiltered"
    ).dependsOn(library)

  lazy val websockets =
    module("netty-websockets")().dependsOn(nettyServer)

  lazy val oauth = module("oauth")().dependsOn(jetty, filters)

  lazy val mac = module("mac")().dependsOn(library)

  lazy val oauth2 = module("oauth2")().dependsOn(jetty, filters, mac)

/*
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
*/

    module("netty-uploads")().dependsOn(nettyServer, uploads)
}
