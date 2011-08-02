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

  def charset = "utf-8"

  def status(statusCode: Int)
  @deprecated("renamed status")
  def setStatus(statusCode: Int) { status(statusCode) }

  def outputStream : OutputStream
  @deprecated("renamed outputStream")
  def getOutputStream() = outputStream

  def redirect(url: String) : Unit
  @deprecated("renamed redirect")
  def sendRedirect(url: String) { redirect(url) }

  def header(name: String, value: String)
  @deprecated("renamed header")
  def addHeader(name: String, value: String) { header(name, value) }
  def cookies(cookie: Seq[Cookie])

  @deprecated("Use a ResponseWriter, or underlying")
  private lazy val writer =
    new PrintWriter(new OutputStreamWriter(outputStream, "utf-8"))
  @deprecated("Use a ResponseWriter, or underlying")
  def getWriter() = writer
}
