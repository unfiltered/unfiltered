package unfiltered.response

class DelegatingResponse[+T](val delegate: HttpResponse[T])
extends HttpResponse[T] {
  val underlying = delegate.underlying
  val outputStream = delegate.outputStream
  def status(statusCode: Int) {
    delegate.status(statusCode)
  }
  def redirect(url: String) {
    delegate.redirect(url)
  }
  def header(name: String, value: String) {
    delegate.header(name, value)
  }
}
