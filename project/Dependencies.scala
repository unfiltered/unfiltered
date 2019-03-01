import sbt._, Keys._

object Dependencies {
  val servletApiDep = "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided"

  val specs2Dep = Def.setting {
    "org.specs2" %% "specs2-core" % "4.3.6"
  }

  def okHttp = "com.squareup.okhttp3" % "okhttp" % "3.11.0" :: Nil

  def integrationTestDeps = Def.setting((specs2Dep.value :: okHttp) map { _ % "test" })

  val commonsCodecVersion = "1.11"
  val scalacheckVersion = "1.14.0"
  val scalaXmlVersion = "1.1.1"
  val commonsIoVersion = "2.6"
  val commonsFileUploadVersion = "1.3.3"
  val jettyVersion = "9.4.14.v20181114"
  val nettyVersion = "4.1.13.Final" // TODO https://github.com/unfiltered/unfiltered/issues/414
  val scalatestVersion = Def.setting {
    CrossVersion.partialVersion(scalaVersion.value) match {
      case Some((2, v)) if v >= 13 =>
        "3.0.6-SNAP5"
      case _ =>
        "3.0.5"
    }
  }
  val json4sVersion = "3.6.2"
  val asyncHttpClientVersion = "1.8.17"
  val javaxActivationVersion = "1.1.1"
}
