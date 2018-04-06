import sbt._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = "org.specs2" %% "specs2-core" % "4.0.2"

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.5.0" :: Nil

  def integrationTestDeps = (specs2Dep :: okHttp) map { _ % "test" }

  val commonsCodecVersion = "1.11"
  val scalacheckVersion = "1.13.5"
  val scalaXmlVersion = "1.0.6"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.3.3"
  val jettyVersion = "9.4.8.v20171121"
  val nettyVersion = "4.1.13.Final"
  val scalatestVersion = "3.0.5-M1"
  val json4sVersion = "3.5.3"
  val asyncHttpClientVersion = "1.8.17"
  val scribeJavaVersion = "3.3.0"
}
