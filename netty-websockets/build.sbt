description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(Shared.integrationTestDeps _)
