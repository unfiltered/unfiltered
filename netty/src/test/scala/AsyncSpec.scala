package unfiltered.netty

object AsyncSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty.{Http => NHttp}

  import dispatch._

  object APlan extends async.Plan with ServerErrorResponse {
    def intent =
      unfiltered.kit.GZip.async {
        case GET(req) & UFPath("/pass") => Pass
        case req@GET(UFPath("/")) =>
          req.respond(ResponseString("test") ~> Ok)
        case req@POST(UFPath("/")) & Params(params) =>
          req.respond(ResponseString(params("key").mkString("")) ~> Ok)
      }
  }

  def setup = _.chunked().handler(APlan).handler(planify {
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
  })

  "A Server" should {
    "respond to requests" in {
      val (enc, str) = http(host.gzip >:+ { (headers, req) =>
        req >- { str =>
          (headers("content-encoding"), str)
        }
      })
      str must_=="test"
      enc must_== Seq("gzip")
    }
    "respond to POST" in {
      val value = List.tabulate(1024){ _ => "unfiltered"}.mkString("!")
      val (enc, str) = http(host << Map("key" -> value) >:+ { (heads, req) =>
        req >- { str => (heads("content-encoding"), str) }
      })
      str must_== value
      enc must beEmpty
    }
    "pass upstream on Pass, respond in last handler" in {
      http(host / "pass" as_str) must_=="pass"
    }
  }
}
