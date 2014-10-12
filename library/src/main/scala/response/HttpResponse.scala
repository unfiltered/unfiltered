package unfiltered.response

import java.io.OutputStream

// T is covariant so e.g. a HttpResponse[HttpServletResponse] can be
// supplied when HttpResponse[Any] is expected.
abstract class HttpResponse[+T](val underlying: T) {
  def charset = HttpResponse.UTF8

  def status(statusCode: Int)

  def status: Int

  def outputStream : OutputStream

  /** Sets a redirect */
  def redirect(url: String): Unit

  /** Adds a header */
  def header(name: String, value: String): Unit
}

object HttpResponse {
  val UTF8 = java.nio.charset.Charset.forName("utf-8")
}
