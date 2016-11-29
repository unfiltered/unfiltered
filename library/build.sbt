description := "Core library for describing requests and responses"

unmanagedClasspath in (LocalProject("unfiltered"), Test) ++= {
  (fullClasspath in (local("specs2"), Compile)).value ++
  (fullClasspath in (local("scalatest"), Compile)).value ++
  (fullClasspath in (local("filter"), Compile)).value
}

libraryDependencies ++= Seq(
  "commons-codec" % "commons-codec" % "1.10",
  Common.specs2Dep(scalaVersion.value) % "test",
  "org.scalacheck" %% "scalacheck" % "1.13.4" % "test",
  "joda-time" % "joda-time" % "2.9.6" % "test",
  "org.joda" % "joda-convert" % "1.8.1" % "test"
)

libraryDependencies ++= {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, v)) if v >= 11 =>
      Seq("org.scala-lang.modules" %% "scala-xml" % "1.0.6")
    case _ =>
      Nil
  }
}
