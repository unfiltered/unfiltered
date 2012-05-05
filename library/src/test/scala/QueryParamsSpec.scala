package unfiltered.request

import org.specs._

object QueryParamsSpecJetty
extends unfiltered.spec.jetty.Planned
with QueryParamsSpec

object QueryParamsSpecNetty
extends unfiltered.spec.netty.Planned
with QueryParamsSpec

trait QueryParamsSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
          
    case GET(UFPath("/basic")) & QueryParams(params) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case Nil => ResponseString("what's foo?")
    }
    
    case GET(UFPath("/with-utf")) & QueryParams(params) => params("фыва") match {
      case Seq(foo) => ResponseString("фыва is %s" format foo)
      case Nil => ResponseString("what's foo?")
    }
    
  }

  "Query params basic map" should {
    "map query string params" in {
      http(host / "basic" <<? Map("foo" -> "bar") as_str) must_== "foo is bar"
    }
    "map several string params" in {
      http(host / "basic" <<? Map("foo" -> "bar", "baz" -> "bar") as_str) must_== "foo is bar"
    }
    "map several string params in any order" in {
      http(host / "basic" <<? Map("baz" -> "bar", "foo" -> "bar", "bin" -> "bon") as_str) must_== "foo is bar"
    }
    "pass unrecognized params" in {
      http(host / "basic" <<? Map("baz" -> "bar") as_str) must_== "what's foo?"
    }
    "work with utf keys and values" in {
      http(host / "with-utf" <<? Map("фыва" -> "олдж") as_str) must_== "фыва is олдж"
    }
  }
  
}
