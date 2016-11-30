description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <++= scalaVersion(Common.integrationTestDeps _)
