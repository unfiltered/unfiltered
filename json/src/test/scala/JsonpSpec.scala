package unfiltered.request

import org.specs._

object JsonpSpec extends Specification  with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  class TestPlan extends unfiltered.filter.Planify({
    case GET(UFPath("/jsonp") & Jsonp(callback)) => ResponseString(callback.wrap("[42]"))
    case GET(UFPath("/jsonp.json") & Jsonp(callback)) => ResponseString(callback.wrap("[42]"))
    case GET(UFPath("/jsonp/optional") & Jsonp.Optional(callback)) => ResponseString(callback.wrap("[42]"))
    case GET(UFPath("/jsonp/lift-json") & Jsonp(callback)) => callback respond {
      import net.liftweb.json.JsonAST._
      JArray(JInt(42) :: Nil)
    }

    case GET(UFPath("/jsonp/lift-json/optional") & Jsonp.Optional(callback)) => callback respond {
      import net.liftweb.json.JsonAST._
      import net.liftweb.json.JsonDSL._
      "answer" -> Seq(42)
    }

    case _ => ResponseString("bad req")
  })

  def setup = { _.filter(new TestPlan) }

  "Jsonp should" should {
    "match an text/javascript accepts request with callback, wrapping response body in callback" in {
      val resp = http(host / "jsonp" <:< Map("Accept" -> "text/javascript") <<? Map("callback" -> "onResp") as_str)
      resp must_=="onResp([42])"
    }
    "match an */* accepts request with path extension and callback, wrapping response body in callback" in {
      val resp = http(host / "jsonp.json" <:< Map("Accept" -> "*/*") <<? Map("callback" -> "onResp") as_str)
      resp must_=="onResp([42])"
    }
   "not match an text/javascript accepts request without a callback" in {
      val resp = http(host / "jsonp" <:< Map("Accept" -> "text/javascript") as_str)
      resp must_=="bad req"
    }
    "optionally match an text/javascript accepts request with callback, wrapping response body in callback" in {
      val resp = http(host / "jsonp" / "optional" <:< Map("Accept" -> "text/javascript") <<? Map("callback" -> "onResp") as_str)
      resp must_=="onResp([42])"
    }
    "optionaly match an application/json accepts request without a callback, return unwrapped response body" in {
      val resp = http(host / "jsonp" / "optional" <:< Map("Accept" -> "application/json") as_str)
      resp must_=="[42]"
    }
    "produce a jsonp response, wrapping response body in callback" in {
      val (body, contentType) = http(host / "jsonp" / "lift-json"
          <:< Map("Accept" -> "text/javascript") <<? Map("callback" -> "onResp") >+ { r =>
        (r as_str, r >:> { _.filterKeys { _ == "Content-Type" } })
      })

      body must_=="""onResp([42])"""
      contentType must haveValue(Set("text/javascript; charset=utf-8"))
    }
    "optionally produce a json response when callback is missing" in {
      val (body, contentType) = http(host / "jsonp" / "lift-json" / "optional"
          <:< Map("Accept" -> "application/json") >+ { r =>
        (r as_str, r >:> { _.filterKeys { _ == "Content-Type" } })
      })

      body must_=="""{"answer":[42]}"""
      contentType must haveValue(Set("application/json; charset=utf-8"))
    }
  }
}
