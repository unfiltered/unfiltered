description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

// TODO(*): do we really need em all? let's pear back after we get a compiling build
libraryDependencies <++= scalaVersion(v =>
  ("io.netty" % "netty-all" % "4.0.13.Final") +:
  Common.integrationTestDeps(v)
)
