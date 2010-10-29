package unfiltered.netty

object ServerSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty.{Http => NHttp}
  
  import dispatch._
  
  def setup = NHttp(_).handler(cycle.Planify({
    case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
  }))
  
  "A Server" should {
    "respond to requests" in {
      Http(host as_str) must_=="test"
    }
  }
}
