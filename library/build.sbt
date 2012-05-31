description := "Core library for describing requests and responses"

unmanagedClasspath in (LocalProject("unfiltered"), Test) <++=
  (fullClasspath in (local("spec"), Compile),
   fullClasspath in (local("filter"), Compile)) map { (s, f) =>
    s ++ f
}

libraryDependencies <++= scalaVersion(v => Seq(
  "commons-codec" % "commons-codec" % "1.4",
  Shared.specsDep(v) % "test"
))
