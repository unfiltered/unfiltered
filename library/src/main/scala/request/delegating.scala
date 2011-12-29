package unfiltered.request

class DelegatingRequest[+T](val delegate: HttpRequest[T])
extends HttpRequest(delegate.underlying) {
  def inputStream = delegate.inputStream
  def reader = delegate.reader
  def protocol = delegate.protocol
  def method = delegate.method
  def uri = delegate.uri
  def parameterNames = delegate.parameterNames
  def parameterValues(param: String) = delegate.parameterValues(param)
  def headers(name: String) = delegate.headers(name)
  @deprecated("use the unfiltered.request.Cookies request extractor instead")
  def cookies = delegate.cookies
  def isSecure = delegate.isSecure
  def remoteAddr = delegate.remoteAddr
}
