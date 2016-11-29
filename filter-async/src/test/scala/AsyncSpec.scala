package unfiltered.filter
import org.specs2.mutable._

object AsyncSpec extends Specification with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  object APlan extends async.Plan  {
    def intent = {
      case GET(UFPath("/pass")) => Pass
      case req@GET(UFPath("/")) =>
        req.respond(ResponseString("test") ~> Ok)
    }
  }

  def setup = _.plan(APlan).plan(Planify {
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  })

  "An Async Filter Server" should {
    "respond to requests" in {
      http(host).as_string must_== "test"
    }
    "pass upstream on Pass, respond in last handler" in {
      http(req(host / "pass")).as_string must_== "pass"
    }
  }
}
