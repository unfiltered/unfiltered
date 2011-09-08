package unfiltered.netty

object AsyncSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty.{Http => NHttp}

  import dispatch._

  object APlan extends async.Plan with ServerErrorResponse {
    def intent = {
      case GET(UFPath("/pass")) => Pass
      case req@GET(UFPath("/")) =>
        req.respond(ResponseString("test") ~> Ok)
    }
  }

  def setup = _.handler(APlan).handler(planify {
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  })

  "A Server" should {
    "respond to requests" in {
      http(host as_str) must_=="test"
    }
    "pass upstream on Pass, respond in last handler" in {
      http(host / "pass" as_str) must_=="pass"
    }
  }
}
