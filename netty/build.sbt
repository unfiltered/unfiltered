description := "Netty server binding module"

unmanagedClasspath in (local("netty"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

val nettyVersion = "4.1.6.Final"

libraryDependencies ++= {
  ("io.netty" % "netty-codec-http" % nettyVersion) +:
  ("io.netty" % "netty-handler" % nettyVersion) +:
  Common.integrationTestDeps(scalaVersion.value)
}
