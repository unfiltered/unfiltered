package unfiltered.request

/** Note that extractors based on this ignore anything beyond a semicolon in a header */
class RequestHeader(val name: String) {
  def unapply[T](req: HttpRequest[T]) = {
    def split(raw: String) = raw.split(",") map {
      _.trim.takeWhile { _ != ';' } mkString
    }
    
    def headers(e: Iterator[String]): List[String] =
      List.fromIterator(e).flatMap(split)
    
    headers(req.headers(name)) match {
      case Nil => None
      case hs => Some(hs)
    }
  }
  def apply[T](req: HttpRequest[T]) = req.headers(name).toList
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
object XForwardedFor extends RequestHeader("X-Forwarded-For") 

object Charset {
  val Setting = """.*;.*\bcharset=(\S+).*""".r
  def unapply[T](req: HttpRequest[T]) = {
    List.fromIterator(req.headers(RequestContentType.name)).flatMap {
      case Setting(cs) => (cs, req) :: Nil
      case _ => Nil
    }.firstOption
  }
}
