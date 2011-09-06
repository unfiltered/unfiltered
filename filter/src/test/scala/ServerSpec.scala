package unfiltered.server

import org.specs._

object ServerSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def setup = _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("test") ~> Ok
    case r @ GET(UFPath("/addr")) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor") & RemoteAddr(addr)) => ResponseString(addr) ~> Ok
  })

  "A Server" should {
    "respond to requests" in {
      http(host as_str) must_=="test"
    }
    "provide a remote address" in {
      http(host / "addr" as_str) must_=="127.0.0.1"
    }
    "provide a remote address accounting for X-Forwared-For header" in {
      http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "66.108.150.228") as_str) must_=="66.108.150.228"
    }
    "provide a remote address accounting for X-Forwared-For header filtering private addresses" in {
      http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "172.31.255.255") as_str) must_=="127.0.0.1"
    }
  }
}
