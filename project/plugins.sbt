resolvers ++= Seq(
  "less is" at "http://repo.lessis.me",
  "coda" at "http://repo.codahale.com")

addSbtPlugin("me.lessis" % "ls-sbt" % "0.1.0")

//addSbtPlugin("com.typesafe.sbteclipse" % "sbteclipse-plugin" % "2.0.0-RC1")

//resolvers += "sbt-idea-repo" at "http://mpeltonen.github.com/maven/"

//addSbtPlugin("com.github.mpeltonen" % "sbt-idea" % "1.0.0")

resolvers += "Scala-Tools Maven2 Snapshots Repository" at "http://scala-tools.org/repo-snapshots"

addSbtPlugin("org.ensime" % "ensime-sbt-cmd" % "0.0.7")