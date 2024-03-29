package unfiltered.request

class DelegatingRequest[+T](val delegate: HttpRequest[T]) extends HttpRequest(delegate.underlying) {
  def inputStream = delegate.inputStream
  def reader = delegate.reader
  def protocol = delegate.protocol
  def method = delegate.method
  def uri = delegate.uri
  def parameterNames = delegate.parameterNames
  def parameterValues(param: String): Seq[String] = delegate.parameterValues(param)
  def headerNames = delegate.headerNames
  def headers(name: String): Iterator[String] = delegate.headers(name)
  def isSecure = delegate.isSecure
  def remoteAddr = delegate.remoteAddr
}
