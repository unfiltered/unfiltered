import sbt._

class Unfiltered(info: ProjectInfo) extends DefaultProject(info) {
  val servlet_api = "javax.servlet" % "servlet-api" % "2.3" % "provided"
}