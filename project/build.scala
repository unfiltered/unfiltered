import sbt._
import Keys._

object Unfiltered {
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
  def module(moduleName: String)(
    projectId: String = "unfiltered-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "unfiltered/" + moduleName.replace("-","/")
  ) = Project(projectId, file(dirName),
              settings = (Common.settings ++
                          ciSettings ++
                          srcPathSetting(projectId, srcPath)
            ))

}
