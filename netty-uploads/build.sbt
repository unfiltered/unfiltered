description := "Uploads plan support using Netty"

unmanagedClasspath in (local("netty-uploads"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)

libraryDependencies += "com.squareup.okhttp3" % "logging-interceptor" % "3.4.2" % "test"

parallelExecution in Test := false
