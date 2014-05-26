package unfiltered.netty

import org.specs2.mutable.Specification

import unfiltered.response.ResponseString

/** Tests a netty server configured with both a plan and a resource handler */
object PlannedResourcesSpec extends Specification with unfiltered.specs2.netty.Served {

   def setup = _.resources(getClass().getResource("/files/")).handler(
     unfiltered.netty.cycle.Planify {
       case _ => ResponseString("planned")
     })

   "A server with resources and plans" should {
     "respond to a resources path" in {
       http(host / "foo.css" as_str) must_==("* { margin:0; }")
     }
     "respond to a plans path" in {
       http(host as_str) must_==("planned")
     }
   }
}
