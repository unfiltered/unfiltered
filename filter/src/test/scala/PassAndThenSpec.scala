package unfiltered.response

import org.specs._

object PassAndThenSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.filter._
  
  import dispatch._
  
  class Around extends Planify({
    case GET(UFPath("/h")) => PassAndThen after {
      case _ => new HeaderName("x-test")("passed")
    }
    case GET(UFPath("/b")) => PassAndThen after {
      case _ => ResponseString("""[{"msg":"howdy partner"}]""")
    }
  })
  
  class TestPlan extends Planify({
    case GET(UFPath("/h")) => JsonContent ~> ResponseString("""[{"msg":"howdy partner"}]""")
    case GET(UFPath("/b")) => JsonContent
  })
  
  def setup = { _.filter(new Around).filter(new TestPlan) }
  
  "PassAndThen" should {
    "Pass and then execute some function setting a body" in {
      val (body, headers) = Http(host / "b" >+ { r => (r as_str, r >:> { h => h }) })
      headers must havePair(("Content-Type" -> Set("application/json; charset=utf-8")))
      body must_=="""[{"msg":"howdy partner"}]"""
    }
    "Pass and then execute some function appending a header" in {
      val (body, headers) = Http(host / "h" >+ { r => (r as_str, r >:> { h => h }) })
      headers must havePair(("x-test" -> Set("passed")))
      headers must havePair(("Content-Type" -> Set("application/json; charset=utf-8")))
      body must_=="""[{"msg":"howdy partner"}]"""
    }
  }
}
