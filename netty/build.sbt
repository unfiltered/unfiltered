description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty-codec-http" % "4.0.14.Final") +:
  Common.integrationTestDeps(v)
)
