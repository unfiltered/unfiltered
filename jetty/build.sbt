description := "Jetty server embedding module"

libraryDependencies := Seq(
  "ch.qos.logback" % "logback-access" % "1.1.7",
  "org.eclipse.jetty" % "jetty-webapp" % Common.jettyVersion
)
