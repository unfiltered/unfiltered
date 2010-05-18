import sbt._

class Unfiltered(info: ProjectInfo) extends ParentProject(info) {
  lazy val library = project("library", "Unfiltered", new DefaultProject(_) {
    val servlet_api = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  })
  val jetty_version = "7.0.2.v20100331"
  lazy val server = project("server", "Unfiltered Server", new DefaultProject(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-webapp" % jetty_version
  }, library)
  lazy val ajp_server = project("ajp-server", "Unfiltered AJP Server", new DefaultProject(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-ajp" % jetty_version
  }, server)
  lazy val demo = project("demo", "Unfiltered Demo", new DefaultProject(_), server)
}