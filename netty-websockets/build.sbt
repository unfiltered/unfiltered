description := "WebSockets plan support using Netty"

unmanagedClasspath in (local("netty-websockets"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)

libraryDependencies += "com.ning" % "async-http-client" % "1.8.12" % "test"
