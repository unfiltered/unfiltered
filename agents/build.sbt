description := "User-Agent request matchers"

unmanagedClasspath in (local("agents"), Test) ++= {
  (fullClasspath in (local("scalatest"), Compile)).value ++
  (fullClasspath in (local("filter"), Compile)).value
}

libraryDependencies ++= {
  Seq(Common.servletApiDep) ++ Common.integrationTestDeps(scalaVersion.value)
}
