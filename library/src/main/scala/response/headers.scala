package unfiltered.response

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object AcceptRanges extends HeaderName("Accept-Ranges")
object Age extends HeaderName("Age")
object Allow extends HeaderName("Allow")
object CacheControl extends HeaderName("Cache-Control")
object ContentDisposition extends HeaderName("Content-Disposition")
object ContentEncoding extends HeaderName("Content-Encoding") {
  val GZip = apply("gzip")
}
object ContentLanguage extends HeaderName("Content-Language")
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
