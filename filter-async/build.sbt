description := "Server binding for Java Servlet 3.0 async filters"

unmanagedClasspath in (local("filter-async"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion { v => Seq(
  Shared.servletApiDep,
  "org.eclipse.jetty" % "jetty-continuation" % Shared.jettyVersion % "compile")
}

