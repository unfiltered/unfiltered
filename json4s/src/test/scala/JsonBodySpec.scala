package unfiltered.request

import org.specs2.mutable._

object JsonBodySpec
extends Specification
with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  class TestPlan extends unfiltered.filter.Plan {
    def intent = {
      case r @ POST(UFPath("/")) => ResponseString(JsonBody(r) match {
        case Some(org.json4s.JArray(a :: b :: Nil)) => "array of 2"
        case _ => "expected json array of 2"
      })
    }
  }

  def setup = { _.plan(new TestPlan) }

  "JsonBody should" should {
    "produce a json parsed representation of the body with accept application/json header" in {
      val resp = http(req(host) <:< Map("Accept" -> "application/json") POST("[4,2]")).as_string
      resp must_== "array of 2"
    }
    "produce a json parsed representation of the body without accept application/json header" in {
      val resp = http(req(host).POST("[4,2]")).as_string
      resp must_== "array of 2"
    }
    "not produce a json parsed representation of a nonjson body" in {
      val resp = http(req(host) <:< Map("Accept" -> "application/json") << Map("foo" -> "bar")).as_string
      resp must_== "expected json array of 2"
    }
    "not produce a json parse representation of a request body when there is no body" in {
      val resp = http(req(host) <:< Map("Accept" -> "application/json") POST("")).as_string
      resp must_== "expected json array of 2"
    }
  }
}
