package unfiltered.netty

object ServerSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty.{Http => NHttp}
  
  import dispatch._
  
  def setup = NHttp(_).handler(cycle.Planify({
    case GET(UFPath("/pass")) => Pass
    case GET(UFPath("/")) => ResponseString("test") ~> Ok
    case r @ GET(UFPath("/addr")) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor") & RemoteAddr(addr)) => ResponseString(addr) ~> Ok
  })).handler(channel.Planify({
    case GET(UFPath("/pass")) => Pass
    case req @ GET(UFPath("/planc")) =>
      req.underlying.respond(ResponseString("planc") ~> Ok)
  })).handler(cycle.Planify({
    case GET(UFPath("/planb")) => ResponseString("planb") ~> Ok
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  }))
  
  "A Server" should {
    "respond to requests" in {
      Http(host as_str) must_=="test"
    }
    "provide a remote address" in {
      Http(host / "addr" as_str) must_=="127.0.0.1"
    }
    "provide a remote address accounting for X-Forwared-For header" in {
      Http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "66.108.150.228") as_str) must_=="66.108.150.228"
    }
    "provide a remote address accounting for X-Forwared-For header filtering private addresses" in {
      Http(host / "addr_extractor" <:< Map("X-Forwarded-For" -> "172.31.255.255") as_str) must_=="127.0.0.1"
    }
    "respond to requests in ustream channel plan" in {
      Http(host / "planc" as_str) must_=="planc"
    }
    "respond to requests in last channel handler" in {
      Http(host / "planb" as_str) must_=="planb"
    }
    "pass upstream on Pass, respond in last handler" in {
      Http(host / "pass" as_str) must_=="pass"
    }
  }
}
