package unfiltered.scalatest.jetty

import _root_.unfiltered.scalatest.Hosted
import org.scalatest.FeatureSpec

trait Served extends FeatureSpec with Hosted {

  import unfiltered.jetty._
  def setup: (Server => Server)
  lazy val server = setup(Http(port))

  override protected def withFixture(test: NoArgTest) {
    server.start();
    try {
          test() // Invoke the test function
    } finally {
      server.stop();
      server.destroy();
    }
  }
}
