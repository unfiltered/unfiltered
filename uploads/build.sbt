description := "Generic support for multi-part uploads"

unmanagedClasspath in (local("uploads"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Seq(
  "commons-io" % "commons-io" % "2.5"
) ++ Common.integrationTestDeps(scalaVersion.value)
