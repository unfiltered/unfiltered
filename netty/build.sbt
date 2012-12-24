description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty" % "3.5.3.Final" withSources()) +:
  Common.integrationTestDeps(v)
)
