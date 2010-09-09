package unfiltered.request

class RequestHeader(val name: String) {
  def unapply(req: HttpRequest) = {
    def split(raw: String): List[String] = raw.split(",") map {
      _.trim.takeWhile { _ != ';' } mkString
    } toList
    
    def headers(e: java.util.Enumeration[_]): List[String] =
      if (e.hasMoreElements) e.nextElement match {
        case v: String => split(v) ++ headers(e)
        case _ => headers(e)
      } else Nil
    
    headers(req.getHeaders(name)) match {
      case Nil => None
      case hs => Some(hs, req)
    }
  }
}

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object Accept extends RequestHeader("Accept")
object AcceptCharset extends RequestHeader("Accept-Charset")
object AcceptEncoding extends RequestHeader("Accept-Encoding")
object AcceptLanguage extends RequestHeader("Accept-Language")
object Authorization extends RequestHeader("Authorization")
object Connection extends RequestHeader("Connection")
object RequestContentType extends RequestHeader("Content-Type")
object Expect extends RequestHeader("Expect")
object From extends RequestHeader("From")
object Host extends RequestHeader("Host")
object IfMatch extends RequestHeader("If-Match")
object IfModifiedSince extends RequestHeader("If-Modified-Since")
object IfNoneMatch extends RequestHeader("If-None-Match")
object IfRange extends RequestHeader("If-Range")
object IfUnmodifiedSince extends RequestHeader("If-Unmodified-Since")
object MaxForwards extends RequestHeader("Max-Forwards")
object ProxyAuthorization extends RequestHeader("Proxy-Authorization")
object Range extends RequestHeader("Range")
object Referer extends RequestHeader("Referer")
object TE extends RequestHeader("TE")
object Upgrade extends RequestHeader("Upgrade")
object UserAgent extends RequestHeader("User-Agent")
object Via extends RequestHeader("Via")
