package unfiltered.spec

import org.specs._
import dispatch._

trait Served extends Specification {
  shareVariables()

  import unfiltered.jetty._
  def setup: (Server => Server)
  val port = 9090
  lazy val server = setup(new Http(port))
  val host = :/("localhost", port)
  
  doBeforeSpec { server.start() }
  doAfterSpec { server.stop() }
}
