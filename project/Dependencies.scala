import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.6.0"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.0.1" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.12"
  val scalacheckVersion = "1.14.0"
  val scalaXmlVersion = "1.2.0"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.19.v20190610"
  val nettyVersion = "4.1.13.Final" // TODO https://github.com/unfiltered/unfiltered/issues/414
  val scalatestVersion = "3.0.8"
  val json4sVersion = "3.6.7"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
