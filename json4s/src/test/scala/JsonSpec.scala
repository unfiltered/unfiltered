package unfiltered.response

import org.specs2.mutable._
import scala.collection.JavaConverters._

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
      val resp = http(req(host) <:< Map("Accept" -> "application/json"))
      val headers = resp.headers.toMultimap.asScala.mapValues(_.asScala.toSet)

      resp.as_string must_== """{"foo":"bar","baz":"boom"}"""
      headers("content-type") must_==(Set("application/json; charset=utf-8"))
    }
  }
}
