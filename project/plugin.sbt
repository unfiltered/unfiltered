addSbtPlugin("com.github.xuwei-k" % "sbt-root-aggregate" % "0.1.0")

addSbtPlugin("org.scalameta" % "sbt-scalafmt" % "2.5.5")

addSbtPlugin("com.github.sbt" % "sbt-unidoc" % "0.6.0")

addSbtPlugin("com.github.sbt" % "sbt-pgp" % "2.3.1")

addSbtPlugin("com.github.sbt" % "sbt-release" % "1.4.0")

if (sys.env.isDefinedAt("GITHUB_ACTION")) {
  Def.settings(
    addSbtPlugin("net.virtual-void" % "sbt-hackers-digest" % "0.1.2")
  )
} else {
  Nil
}
