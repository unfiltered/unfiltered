libraryDependencies <++= sbtVersion(v => Seq(
//  "net.databinder" % "sxr-publish" % "0.2.0",
  "net.databinder" %% "posterous-sbt" % ("0.3.0_sbt%s" format v)
//  "net.databinder" % "pamflet-plugin" % "0.1.4"
))
