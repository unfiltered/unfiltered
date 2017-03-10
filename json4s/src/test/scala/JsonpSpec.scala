package unfiltered.request

import org.specs2.mutable._

object JsonpSpec extends Specification  with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  class TestPlan extends unfiltered.filter.Plan {
    def intent = {
      case GET(UFPath("/jsonp") & Jsonp(callback)) => ResponseString(callback.wrap("[42]"))
      case GET(UFPath("/jsonp.json") & Jsonp(callback)) => ResponseString(callback.wrap("[42]"))
      case GET(UFPath("/jsonp/optional") & Jsonp.Optional(callback)) => ResponseString(callback.wrap("[42]"))
      case GET(UFPath("/jsonp/lift-json") & Jsonp(callback)) => callback respond {
        import org.json4s._
        JArray(JInt(42) :: Nil)
      }

      case GET(UFPath("/jsonp/lift-json/optional") & Jsonp.Optional(callback)) => callback respond {
        import org.json4s.JsonDSL._
        "answer" -> Seq(42)
      }

      case _ => ResponseString("bad req")
    }
  }

  def setup = { _.plan(new TestPlan) }

  "Jsonp should" should {
    "match an text/javascript accepts request with callback, wrapping response body in callback" in {
      val resp = http(req(host / "jsonp" <<? Map("callback" -> "onResp")) <:< Map("Accept" -> "text/javascript")).as_string
      resp must_== "onResp([42])"
    }
    "match an */* accepts request with path extension and callback, wrapping response body in callback" in {
      val resp = http(req(host / "jsonp.json" <<? Map("callback" -> "onResp")) <:< Map("Accept" -> "*/*")).as_string
      resp must_== "onResp([42])"
    }
   "not match an text/javascript accepts request without a callback" in {
      val resp = http(req(host / "jsonp") <:< Map("Accept" -> "text/javascript")).as_string
      resp must_== "bad req"
    }
    "optionally match an text/javascript accepts request with callback, wrapping response body in callback" in {
      val resp = http(req(host / "jsonp" / "optional" <<? Map("callback" -> "onResp")) <:< Map("Accept" -> "text/javascript")).as_string
      resp must_== "onResp([42])"
    }
    "optionaly match an application/json accepts request without a callback, return unwrapped response body" in {
      val resp = http(req(host / "jsonp" / "optional") <:< Map("Accept" -> "application/json")).as_string
      resp must_== "[42]"
    }
    "produce a jsonp response, wrapping response body in callback" in {
      val resp = http(req(host / "jsonp" / "lift-json" <<? Map("callback" -> "onResp"))
          <:< Map("Accept" -> "text/javascript"))

      resp.as_string must_== """onResp([42])"""
      val headers = resp.headers
      headers("content-type") must_==(List("text/javascript; charset=utf-8"))
    }
    "optionally produce a json response when callback is missing" in {
      val resp = http(req(host / "jsonp" / "lift-json" / "optional")
          <:< Map("Accept" -> "application/json"))

      val headers = resp.headers

      resp.as_string must_== """{"answer":[42]}"""
      headers("content-type") must_==(List("application/json; charset=utf-8"))
    }
  }
}
