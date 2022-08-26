import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.10.6" cross CrossVersion.for3Use2_13
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.10.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.15"
  val scalaXmlVersion = Def.setting(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, _)) =>
        "1.3.0"
      case _ =>
        "2.0.1"
    }
  )
  val commonsIoVersion = "2.11.0"
  val commonsFileUploadVersion = "1.4"
  val jettyVersion = "9.4.48.v20220622"
  val nettyVersion = "4.1.80.Final"
  val scalatestVersion = "3.2.11"
  val scalatestScalacheckVersion = Def.setting(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        "3.2.4.0-M1"
      case _ =>
        s"${scalatestVersion}.0"
    }
  )
  val json4sVersion = "3.6.12"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
