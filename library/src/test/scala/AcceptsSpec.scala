package unfiltered.request

import org.specs2.mutable._

object AcceptsSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with AcceptsSpec

object AcceptsSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with AcceptsSpec

trait AcceptsSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}


  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case GET(UFPath(Seg(ext :: Nil)) & Accepts.Json(_)) => ResponseString("json")
    case GET(UFPath(Seg(ext :: Nil)) & Accepts.Jsonp(_)) => ResponseString("javascript")
    case GET(UFPath(Seg(ext :: Nil)) & Accepts.Xml(_)) => ResponseString("xml")
    case GET(UFPath(Seg(ext :: Nil)) & Accepts.Csv(_)) => ResponseString("csv")
    case GET(UFPath(Seg(ext :: Nil)) & Accepts.Html(_)) => ResponseString("html")
  }

  "Accepts should" should {
    "match an application/javascript accepts request as jsonp" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "application/javascript")).as_string
      resp must_== "javascript"
    }
    "match an application/json accepts request as json" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "application/json")).as_string
      resp must_== "json"
    }
    "match a mixed accepts request with json as json" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "application/xhtml+xml,text/xml;q=0.5,application/xml;q=0.9,*/*;q=0.8,application/json")).as_string
      resp must_== "json"
    }
    "match a .json file extension as json when accepts is empty or contains a wildcard" in {
      val resp = http(req(host / "test.json") <:< Map("Accept" -> "*/*")).as_string
      resp must_== "json"
    }
    "match a text/xml accepts request as xml" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "text/xml")).as_string
      resp must_== "xml"
    }
    "match a mixed accepts request with xml as xml" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "text/html,application/xhtml+xml,text/xml;q=0.5,application/xml;q=0.9,*/*;q=0.8")).as_string
      resp must_== "xml"
    }
    "match a .xml file extension as json when accepts is empty or contains a wildcard" in {
      val resp = http(req(host / "test.xml") <:< Map("Accept" -> "*/*")).as_string
      resp must_== "xml"
    }
    "match a text/html accepts request as html" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "text/html")).as_string
      resp must_== "html"
    }
    "match a mixed accepts request with html as html" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "application/octet-stream,text/html,application/pdf;q=0.9,*/*;q=0.8")).as_string
      resp must_== "html"
    }
    "match a .html file extension as html when accepts is empty or contains a wildcard" in {
      val resp = http(req(host / "test.html") <:< Map("Accept" -> "*/*")).as_string
      resp must_== "html"
    }
    "match a text/csv accepts request as csv" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "text/csv")).as_string
      resp must_== "csv"
    }
    "match a mixed accepts request with csv as csv" in {
      val resp = http(req(host / "test") <:< Map("Accept" -> "text/html,text/csv,application/pdf;q=0.9,*/*;q=0.8")).as_string
      resp must_== "csv"
    }
    "match a .csv file extension as csv when accepts is empty or contains a wildcard" in {
      val resp = http(req(host / "test.csv") <:< Map("Accept" -> "*/*")).as_string
      resp must_== "csv"
    }
  }
}
