package unfiltered.scalatest.netty

import _root_.unfiltered.scalatest.Hosted
import org.scalatest.fixture.FixtureFeatureSpec


trait Served extends FixtureFeatureSpec with Hosted {
  import unfiltered.netty._
  def setup: (Int => Server)
  lazy val server = setup(port)

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
