description := "OAuth plans for servlet filters"

unmanagedClasspath in (local("oauth"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v =>
  Seq(Shared.dispatchOAuthDep) ++
  Shared.integrationTestDeps(v)
)
