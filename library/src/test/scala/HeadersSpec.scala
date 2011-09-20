package unfiltered.request

import org.specs._

object HeadersSpecJetty
extends unfiltered.spec.jetty.Planned
with HeadersSpec

object HeadersSpecNetty
extends unfiltered.spec.netty.Planned
with HeadersSpec

trait HeadersSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => P}
  import unfiltered.request.{Connection => Conn}

  import dispatch._

  def seqResp[T](s: Seq[T]) = ResponseString(s match { case Nil => "fail" case _ => "pass" })

  def intent[A, B]: unfiltered.Cycle.Intent[A,B] = {
    case P("/ac") & AcceptCharset(v) => seqResp(v)
    case P("/ae") & AcceptEncoding(v) => seqResp(v)
    case P("/al") & AcceptLanguage(v) => seqResp(v)
    case P("/a") & Authorization(v) => ResponseString("pass")
    case P("/c") & Conn(v) => ResponseString("pass")
    case P("/ct") & RequestContentType(v) => ResponseString("pass")
    case P("/e") & Expect(v) => ResponseString("pass")
    case P("/f") & From(v) => ResponseString("pass")
    case P("/h") & Host(v) => ResponseString("pass")
    case P("/im") & IfMatch(v) => ResponseString("pass")
    case P("/ims") & IfModifiedSince(v) => ResponseString("pass")
    case P("/inm") & IfNoneMatch(v) => seqResp(v)
    case P("/ir") & IfRange(v) => ResponseString("pass")
    case P("/ius") & IfUnmodifiedSince(v) => ResponseString("pass")
    case P("/te") & TE(v) => ResponseString("pass")
    case P("/u") & Upgrade(v) => seqResp(v)
    case P("/ua") & UserAgent(v) => ResponseString("pass")
    case P("/v") & Via(v) => seqResp(v)
    case P("/xff") & XForwardedFor(v) => seqResp(v)
  }
  def get(path: String, header: (String, String)) = {
     val hmap =  Map(header :: Nil:_*)
     http(host / path <:< hmap as_str)
  }
  "Headers" should {
    "parse Accept-Charset" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
      get("ac", ("Accept-Charset", "iso-8859-5, unicode-1-1;q=0.8")) must_=="pass"
    }
   "parse Accept-Encoding" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3
      get("ae", ("Accept-Encoding","gzip;q=1.0, identity; q=0.5, *;q=0")) must_=="pass"
    }
    "parse Accept-Language" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.4
      get("al", ("Accept-Language","da, en-gb;q=0.8, en;q=0.7")) must_=="pass"
    }
    "parse Authorization" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.8
      get("a", ("Authorization","Kind asdfasdf")) must_=="pass"
    }
    "parse Connection" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10
      get("c", ("Connection","close")) must_=="pass"
    }
    "parse Content-Type" in { //http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.17
      get("ct", ("Content-Type","text/html; charset=ISO-8859-4")) must_=="pass"
    }
    "parse Expect" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.20
      get("e", ("Expect","100-continue")) must_=="pass"
    }
    "parse From" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.22
      get("f", ("From","webmaster@w3.org")) must_=="pass"
    }
    "parse Host" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
      get("h", ("Host","www.w3.org")) must_=="pass"
    }
    "parse If-Match" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24
      get("im", ("If-Match","\"xyzzy\", \"r2d2xxxx\", \"c3piozzzz\"")) must_=="pass"
    }
    "parse If-Modified-Since" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
      get("ims", ("If-Modified-Since", "Sat, 29 Oct 2012 19:43:31 GMT")) must_=="pass"
    }
    "parse If-None-Match" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26
       get("inm", ("If-None-Match","close")) must_=="pass"
    }
    "parse If-Range" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.27
      get("ir", ("If-Range","close")) must_=="pass"
    }
    "parse If-Unmodified-Since" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28
      get("ius", ("If-Unmodified-Since","Sat, 29 Oct 1994 19:43:31 GMT")) must_=="pass"
    }
    "parse TE" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.38
      get("te", ("TE","trailers, deflate;q=0.5")) must_=="pass"
    }
    "parse Upgrade" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.42
      get("u", ("Upgrade","HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11")) must_=="pass"
    }
    "parse User-Agent" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.43
      get("ua", ("User-Agent","CERN-LineMode/2.15 libwww/2.17b3")) must_=="pass"
    }
    "parse Via" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.44
      get("v", ("Via","1.0 fred, 1.1 nowhere.com (Apache/1.1)")) must_=="pass"
    }
    "parse X-Forwared-For" in { //  http://en.wikipedia.org/wiki/X-Forwarded-For#Format
      get("xff", ("X-Forwarded-For","client1, proxy1, proxy2")) must_=="pass"
    }
  }
}
