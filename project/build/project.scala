import sbt._

class Unfiltered(info: ProjectInfo) extends DefaultProject(info) {
  val servlet_api = "javax.servlet" % "servlet-api" % "2.3" % "provided"

  val jetty7 = "org.eclipse.jetty" % "jetty-webapp" % "7.0.2.v20100331" % "test"
}