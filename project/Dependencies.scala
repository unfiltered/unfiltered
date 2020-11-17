import sbt._, Keys._
import dotty.tools.sbtplugin.DottyPlugin.autoImport._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.10.5" withDottyCompat scalaVersion.value
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.9.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.15"
  val scalaXmlVersion = "1.3.0"
  val commonsIoVersion = "2.8.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.34.v20201102"
  val nettyVersion = "4.1.54.Final"
  val scalatestVersion = "3.2.3"
  val scalatestScalacheckVersion = "3.2.3.0"
  val json4sVersion = "3.6.10"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
