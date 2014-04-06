description := "monadic api for unfiltered"

unmanagedClasspath in (local("directives"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))


libraryDependencies +=
  "net.databinder.dispatch" %% "dispatch-core" % "0.11.0" % "test"
