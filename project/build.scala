import sbt._
import Keys._

object Unfiltered {
  import Common._
  import java.lang.{ Boolean => JBoolean }

  def id(name: String) = "unfiltered-%s" format name

  def local(name: String) = LocalProject(id(name))

  def srcPathSetting(projectId: String, rootPkg: String) =
    (LocalProject(projectId) / Compile / packageSrc / mappings) ~= {
      defaults: Seq[(File,String)] =>
        defaults.map { case(file, path) =>
          (file, rootPkg + "/" + path)
        }
    }

  def module(moduleName: String)(
    projectId: String = "unfiltered-" + moduleName,
    dirName: String = moduleName,
    srcPath: String = "unfiltered/" + moduleName.replace("-","/")
  ) ={
    val project = Project(projectId, file(dirName))
    project.settings(Common.settings ++
      srcPathSetting(projectId, srcPath))
  }

}
