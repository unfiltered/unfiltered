package unfiltered.request

import org.specs._

object HeadersSpecJetty extends unfiltered.spec.jetty.Served with HeadersSpec {
  def setup = { _.filter(unfiltered.filter.Planify(intent)) }
}

object HeadersSpecNetty extends unfiltered.spec.netty.Served with HeadersSpec {
  def setup = { p =>
    unfiltered.netty.Http(p).handler(unfiltered.netty.cycle.Planify(intent))
  }
}
trait HeadersSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => P}
  import unfiltered.request.{Connection => Conn}

  import dispatch._

  def intent[A, B]: unfiltered.Cycle.Intent[A,B] = {
    case P("/ac") & AcceptCharset(v) => ResponseString(v.toString)
    case P("/ae") & AcceptEncoding(v) => ResponseString(v.toString)
    case P("/al") & AcceptLanguage(v) => ResponseString(v.toString)
    //case P("/ar") & AcceptRanges(v) => ResponseString(v.toString)
    case P("/a") & Authorization(v) => ResponseString(v.toString)
    case P("/c") & Conn(v) => ResponseString(v.toString)
    case P("/ct") & RequestContentType(v) => ResponseString(v.toString)
    case P("/e") & Expect(v) => ResponseString(v.toString)
    case P("/f") & From(v) => ResponseString(v.toString)
    case P("/h") & Host(v) => ResponseString(v.toString)
    case P("/im") & IfMatch(v) => ResponseString(v.toString)
    case P("/ims") & IfModifiedSince(v) => ResponseString(v.toString)
    case P("/inm") & IfNoneMatch(v) => ResponseString(v.toString)
    case P("/ir") & IfRange(v) => ResponseString(v.toString)
    case P("/ius") & IfUnmodifiedSince(v) => ResponseString(v.toString)
    case P("/te") & TE(v) => ResponseString(v.toString)
    case P("/u") & Upgrade(v) => ResponseString(v.toString)
    case P("/ua") & UserAgent(v) => ResponseString(v.toString)
    case P("/v") & Via(v) => ResponseString(v.toString)
    case P("/xff") & XForwardedFor(v) => ResponseString(v.toString)
  }
  def get(path: String, header: (String, String)) = {
     val hmap =  Map(header :: Nil:_*)
     println("sending %s" format hmap)
     Http(host / path <:< hmap as_str)
  }
  "Headers" should {
    "parse Accept-Charset" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.2
      println(get("ac", ("Accept-Charset", "iso-8859-5, unicode-1-1;q=0.8")))
    }
   "parse Accept-Encoding" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.3
      println(get("ae", ("Accept-Encoding","gzip;q=1.0, identity; q=0.5, *;q=0")))
    }
    "parse Accept-Language" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.4
      println(get("al", ("Accept-Language","da, en-gb;q=0.8, en;q=0.7")))
    }
    /*"parse Accept-Ranges" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.5
      println(get("ar", ("Accept-Ranges","bytes")))
    }*/
    // Age? http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.6
    "parse Authorization" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.8
      println(get("a", ("Authorization","Kind asdfasdf")))
    }
    "parse Connection" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10
      println(get("c", ("Connection","close")))
    }
    "parse Content-Type" in { //http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.17
      println(get("ct", ("Content-Type","text/html; charset=ISO-8859-4")))
    }
    "parse Expect" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.20
      println(get("e", ("Expect","100-continue")))
    }
    "parse From" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.22
      println(get("f", ("From","webmaster@w3.org")))
    }
    "parse Host" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.23
      println(get("h", ("Host","www.w3.org")))
    }
    "parse If-Match" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.24
      println(get("im", ("If-Match","\"xyzzy\", \"r2d2xxxx\", \"c3piozzzz\"")))
    }
    "parse If-Modified-Since" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
      println(get("ims", ("If-Modified-Since", "Sat, 29 Oct 2012 19:43:31 GMT")))
    }
    "parse If-None-Match" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.26
      println(get("inm", ("If-None-Match","close")))
    }
    "parse If-Range" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.27
      println(println(get("ir", ("If-Range","close")))) // entity or http date
    }
    "parse If-Unmodified-Since" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.28
      println(get("ius", ("If-Unmodified-Since","Sat, 29 Oct 1994 19:43:31 GMT")))
    }
    "parse TE" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.38
      println(get("te", ("TE","trailers, deflate;q=0.5")))
    }
    "parse Upgrade" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.42
      println(get("u", ("Upgrade","HTTP/2.0, SHTTP/1.3, IRC/6.9, RTA/x11")))
    }
    "parse User-Agent" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.43
      println(get("ua", ("User-Agent","CERN-LineMode/2.15 libwww/2.17b3")))
    }
    "parse Via" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.44
      println(get("v", ("Via","1.0 fred, 1.1 nowhere.com (Apache/1.1)")))
    }
    "parse X-Forwared-For" in { //  http://en.wikipedia.org/wiki/X-Forwarded-For#Format
      println(get("xff", ("X-Forwarded-For","client1, proxy1, proxy2")))
    }
  }
}
