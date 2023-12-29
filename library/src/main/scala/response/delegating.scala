package unfiltered.response

class DelegatingResponse[+T](val delegate: HttpResponse[T]) extends HttpResponse[T](delegate.underlying) {
  val outputStream = delegate.outputStream
  def status(statusCode: Int): Unit = {
    delegate.status(statusCode)
  }
  def status: Int =
    delegate.status
  def redirect(url: String): Unit = {
    delegate.redirect(url)
  }
  def header(name: String, value: String): Unit = {
    delegate.header(name, value)
  }
}
