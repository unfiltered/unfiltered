package unfiltered.request

import org.specs._

object JsonBodySpec extends Specification  with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._

  class TestPlan extends unfiltered.filter.Planify({
    case POST(UFPath("/", JsonBody(js, _))) => ResponseString(js match {
      case  net.liftweb.json.JsonAST.JArray(a :: b :: Nil) => "array of 2"
      case _ => "expected json array of 2"
    })
    case _ => ResponseString("bad req")
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "JsonBody should" should {
    "match an application/json accepts and extract a json parsed representation of the body" in {
      val resp = Http(host <:< Map("Accept" -> "application/json") << "[4,2]" as_str)
      resp must_=="array of 2"
    }
    "not match a non-application/json accepts request" in {
      val resp = Http(host << "[4,2]" as_str)
      resp must_=="bad req"
    }
    "not match an application/json accepts request with and non-json body" in {
      val resp = Http(host <:< Map("Accept" -> "application/json") << Map("foo" -> "bar") as_str)
      resp must_=="bad req"
    }
    "not match an application/json accepts request without a body" in {
      val resp = Http(host <:< Map("Accept" -> "application/json") as_str)
      resp must_=="bad req"
    }
  }
}
