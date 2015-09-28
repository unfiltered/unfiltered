description := "Core library for describing requests and responses"

unmanagedClasspath in (LocalProject("unfiltered"), Test) <++=
  (fullClasspath in (local("specs2"), Compile),
   fullClasspath in (local("scalatest"), Compile),
   fullClasspath in (local("filter"), Compile)) map { (s, st, f) =>
    s ++ st ++ f
}

libraryDependencies <++= scalaVersion(v =>
  "commons-codec" % "commons-codec" % "1.4" ::
  Common.specs2Deps(v).map{_ % "test"}
)

libraryDependencies <<= (libraryDependencies, scalaVersion){
  (dependencies, scalaVersion) =>
  if(!(scalaVersion.startsWith("2.9") || scalaVersion.startsWith("2.10")))
    ("org.scala-lang.modules" %% "scala-xml" % "1.0.1") +: dependencies
  else
    dependencies
}
