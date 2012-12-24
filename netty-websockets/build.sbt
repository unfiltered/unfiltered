description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(Common.integrationTestDeps _)

libraryDependencies += "me.lessis" %% "tubesocks" % "0.1.0" % "test" exclude("io.netty", "netty")
