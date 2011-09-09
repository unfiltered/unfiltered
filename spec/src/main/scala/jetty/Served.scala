package unfiltered.spec.jetty

import org.specs._

trait Planned extends Served {
  import unfiltered.netty.cycle._

  def setup = _.plan(unfiltered.filter.Planify(intent))
  def intent[A,B]: unfiltered.Cycle.Intent[A,B]
}

trait Served extends unfiltered.spec.Hosted {
  shareVariables()

  import unfiltered.jetty._
  def setup: (Server => Server)
  lazy val server = setup(Http(port))
  
  doBeforeSpec { server.start() }
  doAfterSpec { server.stop(); server.destroy() }
}
