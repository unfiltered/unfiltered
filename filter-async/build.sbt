description := "Server binding for Java Servlet 3.0 async filters"

unmanagedClasspath in (local("filter-async"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies += Common.servletApiDep
