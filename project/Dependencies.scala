import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.10.0"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.7.2" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.14"
  val scalaXmlVersion = "1.3.0"
  val commonsIoVersion = "2.7"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.30.v20200611"
  val nettyVersion = "4.1.50.Final"
  val scalatestVersion = "3.1.2"
  val scalatestScalacheckVersion = "3.2.0.0"
  val json4sVersion = "3.6.9"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
