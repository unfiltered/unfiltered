import sbt._

class Unfiltered(info: ProjectInfo) extends ParentProject(info) {
  
  class UnfilteredModule(info: ProjectInfo) extends DefaultProject(info) with sxr.Publish {
    // testing
    lazy val snapshots = "scala-tools snapshots :(" at "http://scala-tools.org/repo-snapshots/"
    lazy val specs = "org.scala-tools.testing" %% "specs" % "1.6.5-SNAPSHOT" % "test"
    lazy val databinderNet = "databinder.net repository" at "http://databinder.net/repo"
    lazy val dpVersion = "0.7.4"
    lazy val dispatchLiftJson = "net.databinder" %% "dispatch-lift-json" % dpVersion % "test"
  }
  
  lazy val library = project("library", "Unfiltered", new UnfilteredModule(_) {
    val servlet_api = "javax.servlet" % "servlet-api" % "2.3" % "provided"
    val codec = "commons-codec" % "commons-codec" % "1.4"
  })
  val jetty_version = "7.0.2.v20100331"
  lazy val server = project("server", "Unfiltered Server", new UnfilteredModule(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-webapp" % jetty_version
  }, library)
  lazy val ajp_server = project("ajp-server", "Unfiltered AJP Server", new UnfilteredModule(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-ajp" % jetty_version
  }, server)
  lazy val demo = project("demo", "Unfiltered Demo", new UnfilteredModule(_), server)

  /** Exclude demo from publish, all other actions run from parent */
  override def dependencies = super.dependencies.filter { d => !(d eq demo) }

  override def managedStyle = ManagedStyle.Maven
  val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
}
