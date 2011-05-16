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
  import unfiltered.request.{Path=>UFPath}

  import dispatch._

  def intent[A, B]: unfiltered.Cycle.Intent[A,B] = {
    case UFPath("/test1") & IfModifiedSince(time) => ResponseString("if-modified-since contains %s value(s)" format time.size)
  }

  "Headers" should {
    "parse http date" in { // http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.25
        val body = Http(host / "test1" <:< Map("If-Modified-Since" -> "Sat, 29 Oct 2012 19:43:31 GMT") as_str)
        body must_=="if-modified-since contains 1 value(s)"
     }
   }
}
