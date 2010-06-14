package unfiltered.response

import java.io.PrintWriter
import javax.servlet.http.HttpServletResponse

object ResponsePackage {
  // make a package object when 2.7 support is dropped
  type ResponseFunction = HttpServletResponse => HttpServletResponse
}
import ResponsePackage.ResponseFunction

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply(res: HttpServletResponse) = res
}

trait Responder extends ResponseFunction {
  def apply(res: HttpServletResponse) = { 
    respond(res)
    res
  }
  def respond(res: HttpServletResponse)
  def ~> (that: ResponseFunction) = new ChainResponse(this andThen that)
}
class ChainResponse(f: ResponseFunction) extends Responder {
  def respond(res: HttpServletResponse) = f(res)
}

case class Redirect(loc: String) extends Responder {
  def respond(res: HttpServletResponse) { res.sendRedirect(loc) }
}

case class Status(code: Int) extends Responder {
  def respond(res: HttpServletResponse) { res.setStatus(code) }
}

// http://www.iana.org/assignments/http-status-codes & http://en.wikipedia.org/wiki/List_of_HTTP_status_codes

object Continue extends Status(100)
object SwitchingProtocols extends Status(101)
object Processing extends Status(102)

object Ok extends Status(200)
object Created extends Status(201)
object Accepted extends Status(202)
object NonAuthoritativeInformation extends Status(203)
object NoContent extends Status(204)
object ResetContent extends Status(205) 
object PartialContent extends Status(206)
object MultiStatus extends Status(207)
object AlreadyReported extends Status(208)
object IMUsed extends Status(226)

object MultipleChoices extends Status(300)
object MovedPermanently extends Status(301)
object Found extends Status(302) 
object SeeOther extends Status(303)
object NotModified extends Status(304)
object UseProxy extends Status(305)
object TemporaryRedirect extends Status(307)

object BadRequest extends Status(400)
object Unauthorized extends Status(401)
object PaymentRequired extends Status(402)
object Forbidden extends Status(403)
object NotFound extends Status(404)
object MethodNotAllowed extends Status(405)
object NotAcceptable extends Status(406)
object ProxyAuthenticationRequired extends Status(407)
object RequestTimeout extends Status(408)
object Conflict extends Status(409)
object Gone extends Status(410)
object LengthRequired extends Status(411)
object PreconditionFailed extends Status(412)
object RequestEntityTooLarge extends Status(413)
object RequestURITooLong extends Status(414)
object UnsupportedMediaType extends Status(415)
object RequestedRangeNotSatisfiable extends Status(416) 
object ExpectationFailed extends Status(417)
object TeaPot extends Status(418)
object TooManyConnections extends Status(421)
object UnprocessableEntity extends Status(422)
object Locked extends Status(423)
object FailedDependency extends Status(424)
object UnorderedCollection extends Status(425)
object UpdateRequired extends Status(426)

object InternalServerError extends Status(500)
object NotImplemented extends Status(501)
object BadGateway extends Status(502)
object ServiceUnavailable extends Status(503)
object GatewayTimeout extends Status(504)
object VersionNotSupported extends Status(505)
object VariantAlsoNegotiates extends Status(506)
object InsufficientStorage extends Status(507)
object LoopDetected extends Status(508)
object NotExtended extends Status(510)

case class ResponseHeader(name: String, values: Iterable[String]) extends unfiltered.response.Responder {
  def respond(res: HttpServletResponse) { 
    values.foreach { v => res.addHeader(name, v) } 
  }
}
class HeaderName(name: String) {
  def apply(value: String*) = ResponseHeader(name, value)
}

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object AcceptRanges extends HeaderName("Accept-Ranges")
object Age extends HeaderName("Age")
object Allow extends HeaderName("Allow")
object CacheControl extends HeaderName("Cache-Control")
object ContentEncoding extends HeaderName("Content-Encoding")
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

case class ContentType(content_type: String) extends Responder {
  def respond(res: HttpServletResponse) {
    res.setContentType("%s; charset=%s".format(content_type, charset))
  }
  def charset = "utf-8"
}
object CssContent extends ContentType("text/css")
object HtmlContent extends ContentType("text/html")

trait ResponseWriter extends Responder {
  def respond(res: HttpServletResponse) {
    val writer = res.getWriter()
    try { write(writer) }
    finally { writer.close() }
  }
  def write(writer: PrintWriter): Unit
}
case class ResponseString(content: String) extends ResponseWriter {
  def write(writer: PrintWriter) { writer.write(content) }
}

case class Html(nodes: scala.xml.NodeSeq) extends ChainResponse(HtmlContent ~> ResponseString(nodes.toString))
