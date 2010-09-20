package unfiltered.request

import org.specs._

object JsonpSpec extends Specification  with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._

  class TestPlan extends unfiltered.filter.Planify({
    case GET(UFPath("/jsonp/with-callback", Jsonp(cb, _))) => ResponseString(cb.wrap("[42]"))
    case GET(UFPath("/jsonp/with-optional-callback", Jsonp.Optional(cb, _))) => ResponseString(cb.wrap("[42]"))
    case _ => ResponseString("bad req")
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "Jsonp should" should {
    "match an application/json accepts request with callback, wrapping response body in callback" in {
      val resp = Http(host / "jsonp" / "with-callback" <:< Map("Accept" -> "application/json") <<? Map("callback" -> "onResp") as_str)
      resp must_=="onResp([42])"
    }
   "not match an application/json accepts request without a callback" in {
      val resp = Http(host / "jsonp" / "with-callback" <:< Map("Accept" -> "application/json") as_str)
      resp must_=="bad req"
    }
    "optionally match an application/json accepts request with callback, wrapping response body in callback" in {
      val resp = Http(host / "jsonp" / "with-optional-callback" <:< Map("Accept" -> "application/json") <<? Map("callback" -> "onResp") as_str)
      resp must_=="onResp([42])"
    }
    "optionaly match an application/json accepts request without a callback, return unwrapped response body" in {
      val resp = Http(host / "jsonp" / "with-optional-callback" <:< Map("Accept" -> "application/json") as_str)
      resp must_=="[42]"
    }  

  }
}
