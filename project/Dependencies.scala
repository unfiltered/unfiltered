import sbt._, Keys._

object Dependencies {
  val servletApiDep = "jakarta.servlet" % "jakarta.servlet-api" % "5.0.0"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.20.2"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.11.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.16.0"
  val scalaXmlVersion = "2.2.0"
  val commonsIoVersion = "2.14.0"
  val commonsFileUploadVersion = "1.5"
  val jettyVersion = "11.0.16"
  val nettyVersion = "4.1.99.Final"
  val scalatestVersion = "3.2.17"
  val scalatestScalacheckVersion = s"${scalatestVersion}.0"
  val json4sVersion = "4.0.6"
  val asyncHttpClientVersion = "1.8.17"
  val activationVersion = "2.1.2"
}
