description := "Json requset matchers and response functions"

unmanagedClasspath in (local("json"), Test) <++=
  (fullClasspath in (local("spec"), Compile),
   fullClasspath in (local("filter"), Compile)) map { (s, f) =>
     s ++ f
   }

libraryDependencies <++= scalaVersion( sv =>
  Seq(sv.split("[.-]").toList match {
    case "2" :: "9" :: _ =>
      "net.liftweb" % "lift-json_2.9.1" % "2.4"
    case _ => "net.liftweb" %% "lift-json" % "2.4"
  }) ++ Shared.integrationTestDeps(sv)
)
