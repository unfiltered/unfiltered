import sbt._

class Unfiltered(info: ProjectInfo) extends ParentProject(info)
  with posterous.Publish
  with pamflet.Actions
{
  class UnfilteredModule(info: ProjectInfo) extends DefaultProject(info)
    with sxr.Publish
  {
    override def packageSrcJar= defaultJarPath("-sources.jar")
    lazy val sourceArtifact = Artifact.sources(artifactID)
    override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)
  }

  /** Allows Unfiltered modules to test themselves using other modules */
  trait IntegrationTesting extends DefaultProject {
    // add to test classpath manually since we don't want to actually depend on these modules
    def testDeps = spec :: jetty :: filter_p :: netty :: netty_server :: Nil
    override def testClasspath = (super.testClasspath /: testDeps) {
      _ +++ _.projectClasspath(Configurations.Compile)
    }
    override def testCompileAction = super.testCompileAction dependsOn
      (testDeps map { _.compile } : _*)
    lazy val specs = specsDependency % "test"
    lazy val dispatch = dispatchDependency % "test"
  }

  /** core unfiltered library */
  lazy val library = project("library", "Unfiltered",
      new UnfilteredModule(_) with IntegrationTesting {
    val codec = "commons-codec" % "commons-codec" % "1.4"
  }, util)
  lazy val filter_p = project("filter", "Unfiltered Filter", new UnfilteredModule(_) with IntegrationTesting {
    lazy val filter = servletApiDependency
  }, library)
  /** file uploads */
  lazy val uploads = project("uploads", "Unfiltered Uploads", new UnfilteredModule(_) with IntegrationTesting {
    lazy val filter = servletApiDependency
    val io = "commons-io" % "commons-io" % "1.4"
    val fileupload = "commons-fileupload" % "commons-fileupload" % "1.2.1"
  }, filter_p)
  /** Base module for Unfiltered library and servers */
  lazy val util = project("util", "Unfiltered Utils", new UnfilteredModule(_))
  val jetty_version = "7.2.2.v20101205"
  /** embedded server*/
  lazy val jetty = project("jetty", "Unfiltered Jetty", new UnfilteredModule(_) {
    val jetty7 = jettyDependency
  }, util)
  /** AJP protocol server */
  lazy val jetty_ajp = project("jetty-ajp", "Unfiltered Jetty AJP", new UnfilteredModule(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-ajp" % jetty_version
  }, jetty)

  lazy val netty_server = project("netty-server", "Unfiltered Netty Server",
    new UnfilteredModule(_) {
      val netty = "org.jboss.netty" % "netty" % "3.2.4.Final" withSources()
    }, util
  )
  lazy val netty = project("netty", "Unfiltered Netty", new UnfilteredModule(_) with IntegrationTesting,
    netty_server, library)

  /** Marker for Scala 2.8-only projects that shouldn't be cross compiled or published */
  trait Only28AndUp

  /** specs  helper */
  lazy val spec = project("spec", "Unfiltered Spec", new DefaultProject(_) with sxr.Publish {
    lazy val specs = specsDependency
    lazy val dispatch = dispatchDependency
  }, jetty, netty)

  /** scala test  helper */
  lazy val scalatest = project("scalatest", "Unfiltered Scalatest", new DefaultProject(_) with sxr.Publish with Only28AndUp {
    lazy val specs = scalatestDependency
    lazy val dispatch = dispatchDependency
  }, jetty, netty)

  /** json extractors */
  lazy val json = project("json", "Unfiltered Json",
      new UnfilteredModule(_) with IntegrationTesting {
    val lift_json =
      if (buildScalaVersion.startsWith("2.7"))
        "net.liftweb" % "lift-json_2.7.7" % "2.2"
      else
        "net.liftweb" % "lift-json_2.8.1" % "2.2"
  }, library)
  def servletApiDependency = "javax.servlet" % "servlet-api" % "2.3" % "provided"

  lazy val scalate = project("scalate", "Unfiltered Scalate",
      new UnfilteredModule(_) with Only28AndUp with IntegrationTesting {
    val scalateVers = "1.4.1"
    val scalateLibs = "org.fusesource.scalate" % "scalate-core" % scalateVers
    val scalateUtils = "org.fusesource.scalate" % "scalate-util" % scalateVers % "test"
    val scalaCompiler = "org.scala-lang" % "scala-compiler" % buildScalaVersion % "test"
    val mockito = "org.mockito" % "mockito-core" % "1.8.5" % "test"
    override def repositories = Set(ScalaToolsSnapshots)
  }, library)
  /** websockets */
  lazy val websockets = project("websockets", "Unfiltered Websockets",
    new UnfilteredModule(_), netty)

  /** oauth */
  lazy val oauth = project("oauth", "Unfiltered OAuth",
    new UnfilteredModule(_) with IntegrationTesting {
    lazy val dispatchOAuth = dispatchOAuthDependency % "test"
  },jetty, filter_p)

  def specsDependency =
    if (buildScalaVersion startsWith "2.7.")
      "org.scala-tools.testing" % "specs" % "1.6.2.2_1.5.0"
    else
      "org.scala-tools.testing" % "specs_2.8.1" % "1.6.7"

  def scalatestDependency =
    "org.scalatest" % "scalatest" % "1.3"

  def dispatchDependency = if(buildScalaVersion startsWith "2.8.1")
      "net.databinder" % "dispatch-mime_2.8.0" % "0.7.8"
    else
      "net.databinder" %% "dispatch-mime" % "0.7.8"

  def dispatchOAuthDependency = if(buildScalaVersion startsWith "2.8.1")
      "net.databinder" % "dispatch-oauth_2.8.0" % "0.7.8"
    else
      "net.databinder" %% "dispatch-oauth" % "0.7.8"

  def jettyDependency = "org.eclipse.jetty" % "jetty-webapp" % jetty_version

  /** Exclude 2.8 projects from cross-buiding actions run from parent */
  override def dependencies = super.dependencies.filter {
    case _: Only28AndUp => buildScalaVersion startsWith "2.8"
    case _ => true
  }

  override def postTitle(vers: String) = "Unfiltered %s" format vers

  lazy val repo = "jboss repo" at "http://repository.jboss.org/nexus/content/groups/public-jboss/"

  override def managedStyle = ManagedStyle.Maven
  val publishTo = "Scala Tools Nexus" at
    "http://nexus.scala-tools.org/content/repositories/releases/"
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
}
