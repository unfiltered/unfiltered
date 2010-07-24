package unfiltered.request

import org.specs._

object ParamsSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  class TestPlan extends unfiltered.Planify({
    case GET(UFPath("/qp", Params(params, _))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }

    case GET(UFPath("/even", Params.Query(q, _))) => 
      (for {
        even <- q("number", Params.first ~> Params.int ~> { _ filter { _ % 2 == 0 } })
      } yield ResponseString(even.get.toString)) orElse BadRequest ~> ResponseString("fail")
    
      case POST(UFPath("/pp", Params(params, _))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "Params" should {
    "extract query params" in {
      Http(host / "qp" <<? Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "extract post params" in {
      Http(host / "pp" << Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "return even number" in {
      Http(host / "even" <<? Map("number" -> "8") as_str) must_=="8"
    }
  }
}
