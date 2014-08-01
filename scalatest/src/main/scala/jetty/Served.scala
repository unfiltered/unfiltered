package unfiltered.scalatest.jetty

import org.scalatest.{ Suite, Outcome }
import unfiltered.jetty.{Http, Server}
import unfiltered.scalatest.Hosted

trait Planned extends Served { self: Hosted =>
  def setup = _.plan(unfiltered.filter.Planify(intent))
  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Suite { self: Hosted =>
  def setup: Server => Server
  def getServer = setup(Http(port))

  override protected def withFixture(test: NoArgTest): Outcome = {
    val server = getServer
    server.start()
    try {
      test() // Invoke the test function
    } finally {
      server.stop()
      server.destroy()
    }
  }
}
