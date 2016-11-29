import sbt._
import Keys._

object Unfiltered extends Build {
  import Common._
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

  private def ciSettings: Seq[Def.Setting[_]] = {
    if (JBoolean.parseBoolean(
      sys.env.getOrElse("TRAVIS", "false"))) Seq(
      logLevel in Global := Level.Warn,
      logLevel in Compile := Level.Warn,
      logLevel in Test := Level.Info
    ) else Seq.empty[Def.Setting[_]]
  }
  private def module(moduleName: String)(
    projectId: String = "unfiltered-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "unfiltered/" + moduleName.replace("-","/")
  ) = Project(projectId, file(dirName),
              settings = (Common.settings ++
                          ciSettings ++
                          srcPathSetting(projectId, srcPath)
            ))

  lazy val unfiltered =
    Project("unfiltered-all",
            file("."),
            settings = Common.settings
    ).aggregate(
            library, filters, filtersAsync , uploads, filterUploads,
            nettyUploads, util, jetty,
            netty, nettyServer, json4s,
            specs2Helpers, scalaTestHelpers, websockets, oauth,  mac,
            oauth2, agents, directives)

  lazy val library: Project =
    module("unfiltered")(
      dirName = "library",
      projectId = "unfiltered"
   ).dependsOn(util)

  lazy val directives =
    module("directives")().dependsOn(library)

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

  lazy val nettyServer =
    module("netty-server")(
      srcPath = "unfiltered/netty"
    ).dependsOn(netty, util)

  lazy val netty = module("netty")().dependsOn(library)

  lazy val specs2Helpers =
    module("specs2")().dependsOn(filters, jetty, nettyServer)

  lazy val scalaTestHelpers =
    module("scalatest")().dependsOn(filters, jetty, nettyServer)

  lazy val json4s =
    module("json4s")(
      srcPath = "unfiltered"
    ).dependsOn(library)

  lazy val websockets =
    module("netty-websockets")().dependsOn(nettyServer)

  lazy val oauth = module("oauth")().dependsOn(jetty, filters, directives)

  lazy val mac = module("mac")().dependsOn(library)

  lazy val oauth2 = module("oauth2")().dependsOn(jetty, filters, mac, directives)

  lazy val nettyUploads = module("netty-uploads")().dependsOn(nettyServer, uploads)
}
