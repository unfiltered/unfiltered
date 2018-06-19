import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    val v = CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        "4.3.0"
      case _ =>
        "4.2.0"
    }
    "org.specs2" %% "specs2-core" % v
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.10.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.11"
  val scalacheckVersion = "1.14.0"
  val scalaXmlVersion = "1.1.0"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.3.3"
  val jettyVersion = "9.4.8.v20171121"
  val nettyVersion = "4.1.13.Final"
  val scalatestVersion = Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        "3.0.6-SNAP1"
      case _ =>
        "3.0.5-M1"
    }
  }
  val json4sVersion = "3.5.4"
  val asyncHttpClientVersion = "1.8.17"
  val scribeJavaVersion = "3.3.0"
}
