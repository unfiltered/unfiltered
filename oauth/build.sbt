description := "OAuth plans for servlet filters"

unmanagedClasspath in (local("oauth"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies += "com.github.scribejava" % "scribejava-core" % "3.2.0" % "test"

libraryDependencies ++= Common.integrationTestDeps(scalaVersion.value)
