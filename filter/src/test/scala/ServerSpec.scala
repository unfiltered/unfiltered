package unfiltered.server

import org.specs._

object ServerSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  def setup = _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
    case GET(UFPath("/addr", r)) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor", RemoteAddr(addr, _))) => ResponseString(addr) ~> Ok
  })
  
  "A Server" should {
    "respond to requests" in {
      Http(host as_str) must_=="test"
    }
    "provide a remote address" in {
      Http(host / "addr" as_str) must_=="127.0.0.1"
    }
    "provide a remote address" in {
      Http(host / "addr" as_str) must_=="127.0.0.1"
    }
  }
}
