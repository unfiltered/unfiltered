package unfiltered.scalatest.netty

import org.scalatest.Outcome
import unfiltered.netty.Server
import unfiltered.scalatest.Hosted
import org.scalatest.fixture.FeatureSpec
import io.netty.util.ResourceLeakDetector

trait Served extends FeatureSpec with Hosted {

  // Enables paranoid resource leak detection which reports where the leaked object was accessed recently,
  // at the cost of the highest possible overhead (for testing purposes only).
  ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.PARANOID)

  def setup: Int => Server
  def getServer = setup(port)

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
