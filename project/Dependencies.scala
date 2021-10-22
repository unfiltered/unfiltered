import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "4.0.1" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.11.0" cross CrossVersion.for3Use2_13
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.9.2" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.15"
  val scalaXmlVersion = "2.0.0"
  val commonsIoVersion = "2.8.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "10.0.2"
  val nettyVersion = "4.1.69.Final"
  val scalatestVersion = "3.2.9"
  val scalatestScalacheckVersion = "3.2.9.0"
  val json4sVersion = "3.6.11"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
