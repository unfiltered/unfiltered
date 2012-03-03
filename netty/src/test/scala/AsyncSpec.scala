package unfiltered.netty

import org.jboss.netty.handler.codec.http.{HttpResponse=>NHttpResponse,
                                           HttpRequest=>NHttpRequest}

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

  object ACPlan extends async.Plan with ServerErrorResponse {
    import unfiltered.kit.AsyncCycle
    def intent = AsyncCycle.rethrow {
      case GET(req) & UFPath("/pass") =>
        Some(Right(Pass))
      case req@GET(UFPath("/asynccycle")) =>
        Some(Right(ResponseString("ac") ~> Ok))
      case GET(UFPath("/error")) =>
        Some(Left(new RuntimeException("Intententional error")))
    }
  }

  def setup = _.chunked().handler(APlan).handler(ACPlan).handler(planify {
    case GET(UFPath("/pass")) => ResponseString("pass") ~> Ok
    case _ => ResponseString("default") ~> Ok
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
    "respond from AsyncCycle" in {
      val str = http(host.gzip / "asynccycle" as_str)
      str must_=="ac"
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
    "pass upstream on undefined, respond in last handler" in {
      http(host / "foo" as_str) must_=="default"
    }
    "return 500 error on exception" in {
      val resp = try {
        http(host / "error" as_str)
      } catch {
        case StatusCode(n, _) => n
      }
      resp must_== 500
    }
  }
}
