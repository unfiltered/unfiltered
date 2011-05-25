import sbt._

class Plugins(info: ProjectInfo) extends PluginDefinition(info) {
  val sxr_publish = "net.databinder" % "sxr-publish" % "0.2.0"
  val posterous = "net.databinder" % "posterous-sbt" % "0.1.7"
  val pf = "net.databinder" % "pamflet-plugin" % "0.1.4"
}
