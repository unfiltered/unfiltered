import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.5.1"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.14.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.12"
  val scalacheckVersion = "1.14.0"
  val scalaXmlVersion = "1.1.1"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.15.v20190215"
  val nettyVersion = "4.1.13.Final" // TODO https://github.com/unfiltered/unfiltered/issues/414
  val scalatestVersion = Def.setting {
    "3.0.7-RC1"
  }
  val json4sVersion = "3.6.5"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
