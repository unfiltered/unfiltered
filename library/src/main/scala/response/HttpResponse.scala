package unfiltered.response

import java.io.{OutputStream, PrintWriter}
import unfiltered.Cookie

abstract class HttpResponse[+T](val underlying: T) {
  // T is covariant so e.g. a HttpResponse[HttpServletResponse] can be
  // supplied when HttpResponse[Any] is expected.
  def setContentType(contentType: String) : Unit
  def setStatus(statusCode: Int) : Unit
  def getOutputStream() : OutputStream
  def sendRedirect(url: String) : Unit
  def addHeader(name: String, value: String) : Unit
  def cookies(cookie: Seq[Cookie]) : Unit
}
