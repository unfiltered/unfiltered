description := "Uploads plan support using Netty"

unmanagedClasspath in (local("netty-uploads"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)
