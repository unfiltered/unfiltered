package unfiltered.scalatest.netty

import unfiltered.netty.Server
import unfiltered.scalatest.Hosted
import org.scalatest.fixture.FeatureSpec

trait Served extends FeatureSpec with Hosted {

  def setup: Int => Server
  def getServer = setup(port)

  override protected def withFixture(test: NoArgTest) {
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
