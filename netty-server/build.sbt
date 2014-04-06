description := "Netty server embedding module"

unmanagedClasspath in (local("netty-server"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <<= scalaVersion(Common.integrationTestDeps _)
