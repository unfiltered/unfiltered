description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(Common.integrationTestDeps _)

libraryDependencies ++= Seq(
  ("me.lessis" %% "tubesocks" % "0.1.0" % "test")
    .exclude("io.netty", "netty")
    .exclude("com.ning", "async-http-client"),
  /* This version handles failed upgrade requests (ex: ws when only wss is
     served) cleanly. */
  "com.ning" % "async-http-client" % "1.8.12" % "test"
)
