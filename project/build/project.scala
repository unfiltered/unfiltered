import sbt._

class Unfiltered(info: ProjectInfo) extends ParentProject(info) {
  lazy val library = project("library", "Unfiltered", new DefaultProject(_) {
    val servlet_api = "javax.servlet" % "servlet-api" % "2.3" % "provided"
  })
  lazy val server = project("server", "Unfiltered Server", new DefaultProject(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-webapp" % "7.0.2.v20100331"
  }, library)
  lazy val demo = project("demo", "Unfiltered Demo", new DefaultProject(_), server)
}