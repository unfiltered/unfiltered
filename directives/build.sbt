description := "monadic api for unfiltered"

unmanagedClasspath in (local("filter"), Test) <++=
  (fullClasspath in (local("spec"), Compile))
