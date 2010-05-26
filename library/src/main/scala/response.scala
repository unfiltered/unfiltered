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
object NotModified extends Status(HttpServletResponse.SC_NOT_MODIFIED)
object NotFound extends Status(HttpServletResponse.SC_NOT_FOUND)

case class ResponseHeader(name: String, values: Iterable[String]) extends unfiltered.response.Responder {
  def respond(res: HttpServletResponse) { 
    values.foreach { v => res.addHeader(name, v) } 
  }
}
class HeaderName(name: String) {
  def apply(value: String*) = ResponseHeader(name, value)
}

object CacheControl extends HeaderName("Cache-Control")
object ETag extends HeaderName("ETag")


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
