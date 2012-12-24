description := "User-Agent request matchers"

unmanagedClasspath in (local("agents"), Test) <++=
  (fullClasspath in (local("spec"), Compile),
   fullClasspath in (local("filter"), Compile)) map {
     (s, f) => s ++ f
   }

libraryDependencies <++= scalaVersion { v =>
  Seq(Common.servletApiDep) ++ Common.integrationTestDeps(v)
}
