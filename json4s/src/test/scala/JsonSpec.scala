package unfiltered.response

import org.specs2.mutable._

object JsonSpec extends Specification  with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  import org.json4s.JsonDSL._

  class TestPlan extends unfiltered.filter.Plan {
    def intent = {
      case UFPath("/") =>
        Json(("foo" -> "bar") ~ ("baz" -> "boom"))
    }
  }

  def setup = { _.plan(new TestPlan) }

  "Json Response should" should {
    "produce a json response" in {
      val (body, contentType) = http(host <:< Map("Accept" -> "application/json") >+ { r =>
        (r as_str, r >:> { _.filterKeys { _ == "Content-Type" } })
      })
      body must_== """{"foo":"bar","baz":"boom"}"""
      contentType must haveValue(Set("application/json; charset=utf-8"))
    }
  }
}
