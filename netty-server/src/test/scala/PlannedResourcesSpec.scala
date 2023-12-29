package unfiltered.netty

import org.specs2.mutable.Specification
import unfiltered.response.ResponseString

/** Tests a netty server configured with both a plan and a resource handler */
class PlannedResourcesSpec extends Specification with unfiltered.specs2.netty.Served {

  def setup = _.resources(getClass().getResource("/files/")).plan(unfiltered.netty.cycle.Planify { case _ =>
    ResponseString("planned")
  })

  "A server with resources and plans" should {
    "respond to a resources path" in {
      http(host / "foo.css").as_string must_== "* { margin:0; }"
    }
    "respond to a plans path" in {
      http(host).as_string must_== "planned"
    }
  }
}
