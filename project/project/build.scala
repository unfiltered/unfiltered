import sbt._
object PluginDef extends Build {
  lazy val root = Project("plugins", file(".")) dependsOn( plugin )
  lazy val plugin = ProjectRef(file("../../ls"), "plugin")
}
