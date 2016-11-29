description := "Json4s request matchers and response functions"

unmanagedClasspath in (local("json4s"), Test) ++= {
  (fullClasspath in (local("specs2"), Compile)).value ++
  (fullClasspath in (local("filter"), Compile)).value
}

libraryDependencies ++= {
  Seq("org.json4s" %% "json4s-native" % "3.5.0") ++ Common.integrationTestDeps(scalaVersion.value)
}
