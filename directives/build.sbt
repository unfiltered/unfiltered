description := "monadic api for unfiltered"

unmanagedClasspath in (local("directives"), Test) <++=
  (fullClasspath in (local("jetty"), Compile))

unmanagedClasspath in (local("directives"), Test) <++=
  (fullClasspath in (local("filter"), Compile))

