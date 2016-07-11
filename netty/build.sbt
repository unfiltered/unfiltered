description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

val nettyVersion = "4.1.2.Final"

libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty-codec-http" % nettyVersion) +:
  ("io.netty" % "netty-handler" % nettyVersion) +:
  Common.integrationTestDeps(v)
)
