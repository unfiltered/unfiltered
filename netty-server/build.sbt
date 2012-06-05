description := "Netty server embedding module"

unmanagedClasspath in (local("netty-server"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <<= scalaVersion(Shared.integrationTestDeps _)
