package unfiltered.scalatest.jetty

import unfiltered.scalatest.Hosted
import org.scalatest.{Suite, Outcome}

trait Planned extends Served { self: Hosted =>

  def setup = _.plan(unfiltered.filter.Planify(intent))

  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Suite { self: Hosted =>

  import unfiltered.jetty._
  def setup: (Server => Server)
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
