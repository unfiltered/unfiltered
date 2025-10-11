import sbt._
import Keys._

object Dependencies {
  val servletApiDep = "jakarta.servlet" % "jakarta.servlet-api" % "5.0.0"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.22.0"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp-jvm" % "5.2.1" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.19.0"
  val scalaXmlVersion = "2.4.0"
  val commonsIoVersion = "2.20.0"
  val commonsFileUploadVersion = "1.6.0"
  val jettyVersion = "11.0.25"
  val nettyVersion = "4.2.6.Final"
  val scalatestVersion = "3.2.19"
  val scalatestScalacheckVersion = s"${scalatestVersion}.0"
  val json4sVersion = "4.0.7"
  val asyncHttpClientVersion = "1.8.17"
  val activationVersion = "2.1.4"
}
