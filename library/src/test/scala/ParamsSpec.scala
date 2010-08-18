package unfiltered.request

import org.specs._

object ParamsSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import Params._

  import dispatch._

  /** Used for extract test */
  object Number extends Params.Extract("number", Params.first ~> Params.int)

  class TestPlan extends unfiltered.Planify({
    case UFPath("/basic", Params(params, _)) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }

    case POST(UFPath("/extract", Params(Number(num, _), _))) =>
      ResponseString(num.toString)

    case GET(UFPath("/int", Params(params, _))) =>
      val expected = for {
        even <- lookup("number") is (required, ()) is(int, ())
      } yield ResponseString(even.get.toString)
      expected(params) orElse { fails =>
        BadRequest ~> ResponseString(
          fails map { _._1  } mkString ","
        )
      }

    case GET(UFPath("/even", Params(params, _))) => 
      val expected = for {
        even <- lookup("number") is (required, "missing") is
          (int, "nonnumber") is (pred { _ % 2 == 0}, "odd")
        whatever <- lookup("what") is (required, "bad")
      } yield ResponseString(even.get.toString)
      expected(params) orElse { fails =>
        BadRequest ~> ResponseString(
          fails map { fail => fail._1 + ":" + fail._2 } mkString ","
        )
      }
    
    case GET(UFPath("/str", Params(params, _))) => 
      val expected = for {
        str <- lookup("param") is(optional, 0) is({ _ map int}, 0)
        req <- lookup("req") is(required, 400)
      } yield ResponseString(str.get.getOrElse(0).toString)
      expected(params) orElse { fails =>
        BadRequest ~> Status(fails.first._2) ~> ResponseString("fail")
      }

  })
  
  def setup = { _.filter(new TestPlan) }
  
  "Params basic map" should {
    "map query string params" in {
      Http(host / "basic" <<? Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "map post params" in {
      Http(host / "basic" << Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
  }
  "Params Extract matcher" should {
    "match and return number" in {
      Http(host / "extract" << Map("number" -> "8") as_str) must_=="8"
    }
    "not match a non-number" in {
      Http.when(_ == 404)(host / "extract" << Map("number" -> "8a") as_str)
    }
  }
  "Params Query expression" should {
    "return a number" in {
      Http(host / "int" <<? Map("number" -> "8") as_str) must_=="8"
    }
    "not match on non-number" in {
      Http.when(_ == 400)(host / "int" <<? Map("number" -> "8a") as_str) must_=="number"
    }
    "return even number" in {
      Http(host / "even" <<? Map("number"->"8","what"->"foo") as_str) must_=="8"
    }
    "fail on non-number" in {
      Http.when(_ == 400)(
        host / "even" <<? Map("number"->"eight", "what"->"") as_str
      ) must_== "number:nonnumber"
    }
    "fail on odd number" in {
      Http.when(_ == 400)(host / "even" <<? Map("number" -> "7") as_str) must_=="number:odd,what:bad"
    }
    "fail on not present" in {
      Http.when(_ == 400)(host / "even" as_str) must_=="number:missing,what:bad"
    }
    "return zero if param not an int" in {
      Http(host / "str" <<? Map("param"->"hi","req"->"whew") as_str) must_=="0"
    }
  }
}
