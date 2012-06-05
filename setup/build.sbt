organization := "net.databinder"

version := "0.6.3"

crossScalaVersions := Seq("2.8.1", "2.8.2",
                   "2.9.0-1", "2.9.1", "2.9.1-1", "2.9.2")

scalaVersion := "2.8.2"

scalacOptions ++=
  Seq("-Xcheckinit", "-encoding", "utf8", "-deprecation", "-unchecked")

parallelExecution in Test := false // :( test servers collide on same port

homepage := Some(new java.net.URL("http://unfiltered.databinder.net/"))

publishMavenStyle := true

publishTo := Some("releases" at
          "https://oss.sonatype.org/service/local/staging/deploy/maven2")

publishArtifact in Test := false

licenses := Seq("MIT" -> url("http://www.opensource.org/licenses/MIT"))

pomExtra := (
  <scm>
    <url>git@github.com:unfiltered/unfiltered.git</url>
    <connection>scm:git:git@github.com:unfiltered/unfiltered.git</connection>
  </scm>
  <developers>
    <developer>
      <id>n8han</id>
      <name>Nathan Hamblen</name>
      <url>http://twitter.com/n8han</url>
    </developer>
    <developer>
      <id>softprops</id>
      <name>Doug Tangren</name>
      <url>http://twitter.com/softprops</url>
    </developer>
  </developers>
)
