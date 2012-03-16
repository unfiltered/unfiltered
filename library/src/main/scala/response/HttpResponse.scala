package unfiltered.response

import java.io.{OutputStream, PrintWriter, OutputStreamWriter}
import unfiltered.Cookie

// T is covariant so e.g. a HttpResponse[HttpServletResponse] can be
// supplied when HttpResponse[Any] is expected.
abstract class HttpResponse[+T](val underlying: T) {
  @deprecated("Use unfiltered.response.ContentType or addHeader directly")
  def setContentType(contentType: String) {
    header("Content-Type", contentType)
  }

  def charset = HttpResponse.UTF8

  def status(statusCode: Int)
  @deprecated("renamed status")
  def setStatus(statusCode: Int) { status(statusCode) }

  def outputStream : OutputStream
  @deprecated("renamed outputStream")
  def getOutputStream() = outputStream

  /** Sets a redirect */
  def redirect(url: String) : Unit
  @deprecated("renamed redirect")
  def sendRedirect(url: String) { redirect(url) }

  /** Adds a header */
  def header(name: String, value: String)
  @deprecated("renamed header")
  def addHeader(name: String, value: String) { header(name, value) }

  @deprecated("use response combinator response.SetCookies(cookies)")
  def cookies(cookie: Seq[Cookie])

  @deprecated("Use a ResponseWriter, or underlying")
  private lazy val writer =
    new PrintWriter(new OutputStreamWriter(outputStream, charset))
  @deprecated("Use a ResponseWriter, or underlying")
  def getWriter() = writer
}

object HttpResponse {
  val UTF8 = java.nio.charset.Charset.forName("utf-8")
}
