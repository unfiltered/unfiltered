description := "Facilitates testing Unfiltered servers with ScalaTest"

libraryDependencies <++= scalaVersion(v => Seq(v.split('.').toList match {
  case "2" :: "8" :: _ => "org.scalatest" % "scalatest_2.8.2" % "1.5.1"
  case "2" :: "9" :: "1" :: _ => "org.scalatest" % "scalatest_2.9.1" % "1.6.1"
  case "2" :: "9" :: _ => "org.scalatest" % "scalatest_2.9.0-1" % "1.6.1"
  case _ => sys.error("ScalaTest not supported for scala version %s" format v)
}) ++ Shared.dispatchDeps)
