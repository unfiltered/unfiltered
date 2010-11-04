package unfiltered.netty

object ServerSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty.{Http => NHttp}
  
  import dispatch._
  
  def setup = NHttp(_).handler(cycle.Planify({
    case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
    case GET(UFPath("/addr", r)) => ResponseString(r.remoteAddr) ~> Ok
    case GET(UFPath("/addr_extractor", RemoteAddr(addr, _))) => ResponseString(addr) ~> Ok
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
  }
}
