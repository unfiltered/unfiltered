package unfiltered.scalatest.jetty

import org.scalatest._
import unfiltered.jetty.Server
import unfiltered.scalatest.Hosted

trait Planned extends Served { self: Hosted =>
  def setup = _.plan(unfiltered.filter.Planify(intent))
  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends TestSuite { self: Hosted =>
  def setup: Server => Server
  def getServer = setup(Server.http(port))

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
