package unfiltered.filter
import org.specs._

object AsyncSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  object APlan extends async.Plan  {
    def intent = {
      case GET(UFPath("/pass")) => Pass
      case req@GET(UFPath("/")) =>
        req.respond(ResponseString("test") ~> Ok)
    }
  }

  def setup = _.filter(APlan).filter(Planify {
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  })

  "An Async Filter Server" should {
    "respond to requests" in {
      http(host as_str) must_=="test"
    }
    "pass upstream on Pass, respond in last handler" in {
      http(host / "pass" as_str) must_=="pass"
    }
  }
}
