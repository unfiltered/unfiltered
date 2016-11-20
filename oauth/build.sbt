description := "OAuth plans for servlet filters"

unmanagedClasspath in (local("oauth"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <++= scalaVersion(v =>
  Common.integrationTestDeps(v)
)

libraryDependencies += "com.github.scribejava" % "scribejava-core" % "3.2.0" % "test"
