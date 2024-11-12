import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.10.6" cross CrossVersion.for3Use2_13
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "4.12.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.16.0"
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
  val jettyVersion = "9.4.50.v20221201"
  val nettyVersion = "4.1.115.Final"
  val scalatestVersion = "3.2.15"
  val scalatestScalacheck = Def.setting(
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, 11)) =>
        "org.scalatestplus" %% "scalacheck-1-15" % "3.2.4.0-M1" % "test"
      case _ =>
        "org.scalatestplus" %% "scalacheck-1-17" % s"${scalatestVersion}.0" % "test"
    }
  )
  val json4sVersion = "3.6.12"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
