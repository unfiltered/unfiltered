package unfiltered.response

import org.specs._

object PassAndThenSpec extends Specification with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  class Around extends unfiltered.Planify({
    case GET(UFPath("/", _)) => PassAndThen after {
      case (req, resp) => (ResponseString("""{"im":"appended"}"""))(resp)
    }
  })
  
  class TestPlan extends unfiltered.Planify({
    case _ => Ok ~> JsonContent
  })
  
  def setup = { _.filter(new Around).filter(new TestPlan) }
  
  "PassAndThen" should {
    "Pass and then execute some function" in {
      val (body, headers) = Http(host >+ { r => (r as_str, r >:> { h => h }) })
      headers must havePair(("Content-Type" -> Set("application/json; charset=utf-8")))
      body must_=="""{"im":"appended"}"""
    }
  }
}
