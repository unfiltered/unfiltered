package unfiltered.response

import org.specs._

object ContentsSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  // Response package is needed for implicit conversion of Contents -> ResponseFunction
  import ResponsePackage._
  
  def setup = { _.filter(unfiltered.Planify {
    
    case GET(UFPath("/test", Params(p, _))) => for {
      x <- Contents(p("x")) ~> ResponseString("missing x")
      y <- Contents(p("y")) ~> ResponseString("missing y")
      z <- Contents(p("z")).orElse(Contents(Seq("3")))
    } yield { 
      ResponseString("contained (x: %s, y: %s)" format(x.head, y.head)) 
    }
    
    case GET(UFPath("/default", Params(p, _))) => for {
      x <- Contents(p("x")).orElse(Contents(Seq("4")))
    } yield { 
      ResponseString("contained (x: %s)" format(x.head)) 
    }
    
  })}
  
  "A Contents Monad" should {
    shareVariables()
    "transform first absent value into to addressed values with ~>" in {
      val resp = Http(host / "test" as_str) 
      resp must_== "missing x" 
    }
    "transform nth absent value into to addressed values with ~>" in {
      val resp = Http(host / "test" <<? Map("x" -> 1) as_str)
      resp must_== "missing y" 
    }
    "yield with present values" in {
      val resp = Http(host / "test" <<? Map("x" -> 1, "y" -> 2) as_str)
      resp must_== "contained (x: 1, y: 2)"
    }
    "yield with default value" in {
      val resp = Http(host / "default" as_str)
      resp must_== "contained (x: 4)"
    }
  }
}