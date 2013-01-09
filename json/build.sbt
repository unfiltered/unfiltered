description := "Json requset matchers and response functions"

unmanagedClasspath in (local("json"), Test) <++=
  (fullClasspath in (local("spec"), Compile),
   fullClasspath in (local("filter"), Compile)) map { (s, f) =>
     s ++ f
   }

libraryDependencies <++= scalaVersion( sv =>
  Seq("org.json4s" %% "json4s-native" % "3.2.3") ++ Common.integrationTestDeps(sv)
)
