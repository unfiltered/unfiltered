description := "Support for multi-part uploads for servlet filters"

unmanagedClasspath in (local("filter-uploads"), Test) ++=
  (fullClasspath in (local("specs2"), Compile)).value

libraryDependencies ++= Seq(
  Common.servletApiDep,
  "commons-fileupload" % "commons-fileupload" % "1.3.1"
) ++ Common.integrationTestDeps(scalaVersion.value)
