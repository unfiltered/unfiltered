package unfiltered.scalatest.jetty

import unfiltered.scalatest.Hosted
import org.scalatest.FeatureSpec

trait Served extends FeatureSpec with Hosted {

  import unfiltered.jetty._
  def setup: (Server => Server)
  def getServer = setup(Http(port))

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
