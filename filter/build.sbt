description := "Server binding for Java Servlet filters"

unmanagedClasspath in (local("filter"), Test) <++=
  (fullClasspath in (local("spec"), Compile))

libraryDependencies += Shared.servletApiDep
