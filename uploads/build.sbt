description := "Generic support for multi-part uploads"

unmanagedClasspath in (local("uploads"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <++= scalaVersion(v => Seq(
  "commons-io" % "commons-io" % "2.4"
) ++ Common.integrationTestDeps(v))
