description := "OAuth plans for servlet filters"

unmanagedClasspath in (local("oauth"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <++= scalaVersion(v =>
  Seq(Common.dispatchOAuthDep % "test") ++
  Common.integrationTestDeps(v)
)
