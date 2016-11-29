description := "Netty server embedding module"

unmanagedClasspath in (local("netty-server"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)
