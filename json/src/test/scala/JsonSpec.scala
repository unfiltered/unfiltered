package unfiltered.response

import org.specs._

object JsonSpec extends Specification  with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._
  import net.liftweb.json.JsonDSL._
  import net.liftweb.json.JsonParser._

  class TestPlan extends unfiltered.filter.Planify({
    case UFPath("/") =>
      Json(("foo" -> "bar") ~ ("baz" -> "boom"))
  })

  def setup = { _.filter(new TestPlan) }

  "Json Response should" should {
    "produce a json response" in {
      val (body, contentType) = http(host <:< Map("Accept" -> "application/json") >+ { r =>
        (r as_str, r >:> { _.filterKeys { _ == "Content-Type" } })
      })
      body must_=="""{"foo":"bar","baz":"boom"}"""
      contentType must haveValue(Set("application/json; charset=utf-8"))
    }
  }
}
