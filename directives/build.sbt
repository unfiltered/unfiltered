description := "monadic api for unfiltered"

unmanagedClasspath in (local("directives"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

