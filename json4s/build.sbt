description := "Json4s request matchers and response functions"

unmanagedClasspath in (local("json4s"), Test) <++=
  (fullClasspath in (local("specs2"), Compile),
   fullClasspath in (local("filter"), Compile)) map { (s, f) =>
     s ++ f
   }

libraryDependencies <++= scalaVersion( sv =>
  Seq("org.json4s" %% "json4s-native" % "3.2.9") ++ Common.integrationTestDeps(sv)
)
