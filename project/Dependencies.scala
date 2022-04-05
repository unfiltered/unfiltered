import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "4.0.1" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.15.0"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.9.3" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.15"
  val scalaXmlVersion = "2.1.0"
  val commonsIoVersion = "2.11.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "10.0.9"
  val nettyVersion = "4.1.75.Final"
  val scalatestVersion = "3.2.11"
  val scalatestScalacheckVersion = s"${scalatestVersion}.0"
  val json4sVersion = "4.0.5"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
