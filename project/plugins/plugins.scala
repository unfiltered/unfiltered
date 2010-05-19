import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val sxr_publish = "net.databinder" % "sxr-publish" % "0.1.6"
}
