description := "Server binding for Java Servlet filters"

unmanagedClasspath in (local("jetty"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies += Common.servletApiDep
