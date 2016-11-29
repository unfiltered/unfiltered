description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)

libraryDependencies += "me.lessis" %% "tubesocks" % "0.1.0" % "test"// exclude("io.netty", "netty")
