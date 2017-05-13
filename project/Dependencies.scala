import sbt._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  def specs2Dep(sv: String) =
    "org.specs2" %% "specs2-core" % "3.8.9"

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.5.0" :: Nil

  def integrationTestDeps(sv: String) = (specs2Dep(sv) :: okHttp) map { _ % "test" }

  val commonsCodecVersion = "1.10"
  val scalacheckVersion = "1.13.5"
  val jodaTimeVersion = "2.9.9"
  val jodaConvertVersion = "1.8.1"
  val scalaXmlVersion = "1.0.6"
  val commonsIoVersion = "2.5"
  val commonsFileUploadVersion = "1.3.2"
  val jettyVersion = "9.2.21.v20170120"
  val nettyVersion = "4.1.11.Final"
  val scalatestVersion = "3.0.3"
  val json4sVersion = "3.5.2"
  val asyncHttpClientVersion = "1.8.17"
  val scribeJavaVersion = "3.3.0"
}
