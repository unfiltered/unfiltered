import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "4.0.1" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.19.0"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.12.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.17.2"
  val scalaXmlVersion = "2.1.0"
  val commonsIoVersion = "2.11.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "10.0.13"
  val nettyVersion = "4.1.120.Final"
  val scalatestVersion = "3.2.15"
  val scalatestScalacheckVersion = s"${scalatestVersion}.0"
  val json4sVersion = "4.0.7"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
