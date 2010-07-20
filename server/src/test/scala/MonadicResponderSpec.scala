package unfiltered.response

import org.specs._

object MonadicResponderSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  // Response package is needed for implicit conversion of Contents -> ResponseFunction
  import ResponsePackage._
  
  object Name extends Params.Named(
    "name", Params.first ~> Params.trimmed ~> Params.nonempty
  ) { 
    def apply(params: Map[String, Seq[String]]) = params match {
      case Name(n, _) => Some(n)
      case _ => None
    }
  }
  
  object Even extends Params.Named(
    "even", Params.first ~> Params.int ~> { _ filter { _ % 2 == 0 } }
  ) { 
    def apply(params: Map[String, Seq[String]]) = params match {
      case Even(n, _) => Some(n)
      case _ => None
    }
  }
  
  def setup = { _.filter(unfiltered.Planify {  
    case GET(UFPath("/multi", Params(p, _))) => 
      for {
         name <- Name(p) withFail ("we need a name")
         even <- Even(p) withFail ("no even number was supplied")
      } yield {
         ResponseString("we got %s and %s" format (name, even))
      } orFail { errors: List[String] =>
         Status(400) ~> ResponseString(("sorry, no: " :: errors).mkString("\n"))
      }
    case GET(UFPath("/single", Params(p, _))) =>
      for {
         name <- Name(p) orFail { ResponseString("we need a name") }
         even <- Even(p) orFail { ResponseString("no even number was supplied") }
      } yield {
         ResponseString("we got %s and %s" format (name, even))
      }
  })}
  
  "Monadic responders" should {
    shareVariables()
    "Accumulate n errors" in {
      val resp = Http(host / "multi" as_str) 
      resp must_== "sorry, no:\nwe need a name\nno even number was supplied" 
    }
    "Accumulate first single error" in {
      val resp = Http(host / "single" as_str) 
      resp must_== "we need a name" 
    }
    "Accumulate nth single error" in {
      val resp = Http(host / "single" <<? Map("name" -> "n8") as_str) 
      resp must_== "no even number was supplied"   
    }
    "yield to successful pattern matches" in {
      val resp = Http(host / "single" <<? Map("name" -> "n8", "even" -> 2) as_str)
      resp must_== "we got n8 and 2" 
    }
  }
}