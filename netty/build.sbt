description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty-codec-http" % "4.1.0.Beta3") +:
  Common.integrationTestDeps(v)
)
