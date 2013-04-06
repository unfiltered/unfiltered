description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty" % "3.6.4.Final") +:
  Common.integrationTestDeps(v)
)
