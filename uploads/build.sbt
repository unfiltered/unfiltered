description := "Generic support for multi-part uploads"

unmanagedClasspath in (local("uploads"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v => Seq(
  "commons-io" % "commons-io" % "1.4"
) ++ Shared.integrationTestDeps(v))
