package unfiltered.response

import java.io.{OutputStream, PrintWriter, OutputStreamWriter}
import unfiltered.Cookie

trait BaseHttpResponse[+T] {
  val underlying: T

  def charset = HttpResponse.UTF8

  def status(statusCode: Int)

  /** Sets a redirect */
  def redirect(url: String) : Unit

  /** Adds a header */
  def header(name: String, value: String)
}

// T is covariant so e.g. a HttpResponse[HttpServletResponse] can be
// supplied when HttpResponse[Any] is expected.
trait HttpResponse[+T] extends BaseHttpResponse[T] {
  def outputStream : OutputStream
}

object HttpResponse {
  val UTF8 = java.nio.charset.Charset.forName("utf-8")
}
