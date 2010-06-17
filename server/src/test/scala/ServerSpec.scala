package unfiltered.server

import org.specs._

object ServerSpec extends Specification {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  class TestPlan extends unfiltered.Planify({
    case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
  })
  val server = unfiltered.server.Http(8083).filter(new TestPlan)
  val host = :/("localhost", 8083)
  
  doBeforeSpec { server.daemonize }
  
  "A Server" should {
    "respond to requests" in {
      Http(host as_str) must_=="test"
    }
  }
  
  doAfterSpec { server.stop }
}