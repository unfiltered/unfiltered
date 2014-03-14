description := "Server binding for Java Servlet filters"

unmanagedClasspath in (local("filter"), Test) <++=
  (fullClasspath in (local("specs2"), Compile))

libraryDependencies += Common.servletApiDep
