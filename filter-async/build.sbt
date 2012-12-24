description := "Server binding for Java Servlet 3.0 async filters"

unmanagedClasspath in (local("filter-async"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies <++= scalaVersion { v => Seq(
  Common.servletApiDep,
  "org.eclipse.jetty" % "jetty-continuation" % Common.jettyVersion % "compile")
}

