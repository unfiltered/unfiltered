package unfiltered.request

import org.specs2.mutable._

class QueryParamsSpecJetty extends Specification with unfiltered.specs2.jetty.Planned with QueryParamsSpec

class QueryParamsSpecNetty extends Specification with unfiltered.specs2.netty.Planned with QueryParamsSpec

trait QueryParamsSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  def intent[A, B]: unfiltered.Cycle.Intent[A, B] = {

    case GET(UFPath("/basic")) & QueryParams(params) =>
      params("foo") match {
        case Seq(foo) => ResponseString(s"foo is ${foo}")
        case Nil => ResponseString("what's foo?")
      }

    case GET(UFPath("/with-utf")) & QueryParams(params) =>
      params("фыва") match {
        case Seq(foo) => ResponseString(s"фыва is ${foo}")
        case Nil => ResponseString("what's foo?")
      }

  }

  "Query params basic map" should {
    "map query string params" in {
      http(host / "basic" <<? Map("foo" -> "bar")).as_string must_== "foo is bar"
    }
    "map several string params" in {
      http(host / "basic" <<? Map("foo" -> "bar", "baz" -> "bar")).as_string must_== "foo is bar"
    }
    "map several string params in any order" in {
      http(host / "basic" <<? Map("baz" -> "bar", "foo" -> "bar", "bin" -> "bon")).as_string must_== "foo is bar"
    }
    "pass unrecognized params" in {
      http(host / "basic" <<? Map("baz" -> "bar")).as_string must_== "what's foo?"
    }
    "work with utf keys and values" in {
      http(host / "with-utf" <<? Map("фыва" -> "олдж")).as_string must_== "фыва is олдж"
    }
  }

}
