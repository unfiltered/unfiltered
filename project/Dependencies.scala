import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.9.2"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.4.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.14"
  val scalaXmlVersion = "1.2.0"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.27.v20200227"
  val nettyVersion = "4.1.46.Final"
  val scalatestVersion = "3.1.1"
  val scalatestScalacheckVersion = "3.1.1.1"
  val json4sVersion = "3.6.7"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
