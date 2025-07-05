package unfiltered.server

import org.specs2.mutable._

class ServerSpec extends Specification with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  def setup = _.plan(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("test") ~> Ok
    case r @ GET(UFPath("/addr")) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor") & RemoteAddr(addr)) => ResponseString(addr) ~> Ok
  })

  "A Server" should {
    "respond to requests" in {
      http(host).as_string must_== "test"
    }
    "provide a remote address" in {
      http(host / "addr").as_string must_== "[0:0:0:0:0:0:0:1]"
    }
    "provide a remote address accounting for X-Forwarded-For header" in {
      http(
        req(host / "addr_extractor") <:< Map("X-Forwarded-For" -> "66.108.150.228")
      ).as_string must_== "66.108.150.228"
    }
    "provide a remote address accounting for X-Forwarded-For header filtering private addresses" in {
      http(
        req(host / "addr_extractor") <:< Map("X-Forwarded-For" -> "172.31.255.255")
      ).as_string must_== "[0:0:0:0:0:0:0:1]"
    }
  }
}
