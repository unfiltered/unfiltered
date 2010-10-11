package unfiltered.spec.jetty

import org.specs._

trait Served extends unfiltered.spec.Hosted {
  shareVariables()

  import unfiltered.jetty._
  def setup: (Server => Server)
  lazy val server = setup(new Http(port))
  
  doBeforeSpec { server.start() }
  doAfterSpec { server.stop(); server.destroy() }
}
