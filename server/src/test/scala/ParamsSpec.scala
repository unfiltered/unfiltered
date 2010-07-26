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

    case GET(UFPath("/int", Params.Query(query, _))) => 
      query.errors[String] { q => for {
        even <- q("number",
          q.first opt Params.int orError "missing")
      } yield ResponseString(even.toString) } orElse { error =>
        BadRequest ~> ResponseString(error)
      }

    case GET(UFPath("/even", Params.Query(query, _))) => 
      query.errors[String] { q => for {
        even <- q("number",
          q.first err(Params.int, "nonnumber") err(
            _.filter(_ % 2 == 0), "odd"
          ) orError "missing"
        )
      } yield ResponseString(even.toString) } orElse { error =>
        BadRequest ~> ResponseString(error)
      }
    
    case GET(UFPath("/str", Params.Query(query, _))) => 
      query.errors[String] { q =>
        for {
          str <- q("param", q.first)
        } yield ResponseString(str.getOrElse(""))
      } orElse { error =>
        BadRequest ~> ResponseString("fail")
      }

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
    "return a number" in {
      Http(host / "int" <<? Map("number" -> "8") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(host / "int" <<? Map("number" -> "8a") as_str) must_=="missing"
    }
    "return even number" in {
      Http(host / "even" <<? Map("number" -> "8") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(host / "even" <<? Map("number" -> "eight") as_str) must_=="nonnumber"
    }
    "fail on odd number" in {
      Http.when(_ == 400)(host / "even" <<? Map("number" -> "7") as_str) must_=="odd"
    }
    "fail on not present" in {
      Http.when(_ == 400)(host / "even" as_str) must_=="missing"
    }
    "return empty on not present" in {
      Http(host / "str" as_str) must_==""
    }
  }
}
