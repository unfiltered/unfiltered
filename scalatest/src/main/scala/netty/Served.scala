package unfiltered.scalatest.netty

import org.scalatest.{ Suite, Outcome }
import unfiltered.netty.Server
import unfiltered.scalatest.Hosted
import io.netty.util.ResourceLeakDetector

trait Planned extends Served { self: Hosted =>
  def setup = _.handler(unfiltered.netty.cycle.Planify(intent))
  def intent[A, B]: unfiltered.Cycle.Intent[A, B]
}

trait Served extends Suite with Hosted {
  import unfiltered.netty._
  // Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
  // at the cost of the highest possible overhead (for testing purposes only).
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)

  def setup: Http => Http
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
