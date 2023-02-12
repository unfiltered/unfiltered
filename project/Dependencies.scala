import sbt._, Keys._

object Dependencies {
  val servletApiDep = "jakarta.servlet" % "jakarta.servlet-api" % "5.0.0"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.19.2"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.10.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.15"
  val scalaXmlVersion = "2.1.0"
  val commonsIoVersion = "2.11.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "11.0.13"
  val nettyVersion = "4.1.88.Final"
  val scalatestVersion = "3.2.15"
  val scalatestScalacheckVersion = s"${scalatestVersion}.0"
  val json4sVersion = "4.0.6"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
