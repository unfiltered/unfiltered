description := "Uploads plan support using Netty"

unmanagedClasspath in (local("netty-uploads"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v =>
  Shared.integrationTestDeps(v)
)