package unfiltered.response

case class ResponseHeader(name: String, values: Iterable[String]) extends Responder[Any] {
  def respond(res: HttpResponse[Any]): Unit = {
    values.foreach { v => res.header(name, v) }
  }
}

class HeaderName(val name: String) {
  def apply(value: String*) = ResponseHeader(name, value)
}

// https://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object AcceptRanges extends HeaderName("Accept-Ranges")
object Age extends HeaderName("Age")
object Allow extends HeaderName("Allow")
object CacheControl extends HeaderName("Cache-Control")
object Connection extends HeaderName("Connection")
object ContentDisposition extends HeaderName("Content-Disposition")
object ContentEncoding extends HeaderName("Content-Encoding") {
  val GZip = apply("gzip")
}
object ContentLanguage extends HeaderName("Content-Language")
object ContentLength extends HeaderName("Content-Length")
object ContentLocation extends HeaderName("Content-Location")
object ContentMD5 extends HeaderName("Content-MD5")
object ContentRange extends HeaderName("Content-Range")
object Date extends HeaderName("Date")
object ETag extends HeaderName("ETag")
object Expires extends HeaderName("Expires")
object LastModified extends HeaderName("Last-Modified")
object Location extends HeaderName("Location")
object Pragma extends HeaderName("Pragma")
object ProxyAuthenticate extends HeaderName("Proxy-Authenticate")
object RetryAfter extends HeaderName("Retry-After")
object Server extends HeaderName("Server")
object Trailer extends HeaderName("Trailer")
object TransferEncoding extends HeaderName("Transfer-Encoding")
object Vary extends HeaderName("Vary")
object Warning extends HeaderName("Warning")
object WWWAuthenticate extends HeaderName("WWW-Authenticate")

// CORS response headers
// https://www.w3.org/TR/cors/#syntax
object AccessControlAllowOrigin extends HeaderName("Access-Control-Allow-Origin")
object AccessControlExposeHeaders extends HeaderName("Access-Control-Expose-Headers")
object AccessControlMaxAge extends HeaderName("Access-Control-Max-Age")
object AccessControlAllowCredentials extends HeaderName("Access-Control-Allow-Credentials") {
  val True = apply("true")
}
object AccessControlAllowMethods extends HeaderName("Access-Control-Allow-Methods")
object AccessControlAllowHeaders extends HeaderName("Access-Control-Allow-Headers")
