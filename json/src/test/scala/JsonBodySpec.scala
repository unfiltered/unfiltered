package unfiltered.request

import org.specs._

object JsonBodySpec
extends Specification
with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  class TestPlan extends unfiltered.filter.Planify({
    case r @ POST(UFPath("/")) => ResponseString(JsonBody(r) match {
      case Some(net.liftweb.json.JsonAST.JArray(a :: b :: Nil)) => "array of 2"
      case _ => "expected json array of 2"
    })
  })

  def setup = { _.filter(new TestPlan) }

  "JsonBody should" should {
    "produce a json parsed representation of the body with accept application/json header" in {
      val resp = http(host <:< Map("Accept" -> "application/json") << "[4,2]" as_str)
      resp must_=="array of 2"
    }
    "produce a json parsed representation of the body without accept application/json header" in {
      val resp = http(host << "[4,2]" as_str)
      resp must_=="array of 2"
    }
    "not produce a json parsed representation of a nonjson body" in {
      val resp = http(host <:< Map("Accept" -> "application/json") << Map("foo" -> "bar") as_str)
      resp must_=="expected json array of 2"
    }
    "not produce a json parse representation of a request body when there is no body" in {
      val resp = http(host.POST <:< Map("Accept" -> "application/json") as_str)
      resp must_=="expected json array of 2"
    }
  }
}
