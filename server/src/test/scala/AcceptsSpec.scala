package unfiltered.request

import org.specs._

object AcceptSpec extends Specification  with unfiltered.spec.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._

  class TestPlan extends unfiltered.Planify({
    case GET(UFPath(Seg(ext :: Nil), Accepts(fmt, _))) => fmt match {
      case fmt => ResponseString(fmt.toString.drop(1))
    }
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "Accepts should" should {
    "match an application/json accepts request as json" in {
      val resp = Http(host / "test" <:< Map("Accept" -> "application/json")  as_str)
      resp must_=="json"
    }
    "match a .json file extension as json when accepts is empty or contains a wildcard" in {
      val resp = Http(host / "test.json" <:< Map("Accept" -> "*/*") as_str)
      resp must_=="json"
    }
    "match a text/xml accepts request as xml" in {
      val resp = Http(host / "test" <:< Map("Accept" -> "text/xml")  as_str)
      resp must_=="xml"
    }
    "match a .xml file extension as json when accepts is empty or contains a wildcard" in {
      val resp = Http(host / "test.xml" <:< Map("Accept" -> "*/*")  as_str)
      resp must_=="xml"
    }
  }
}
