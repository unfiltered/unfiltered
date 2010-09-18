package unfiltered.request

import java.io.{Reader, InputStream}

abstract class HttpRequest[T](val underlying: T) {
  def getInputStream() : InputStream
  def getReader() : Reader
  def getProtocol() : String
  def getMethod() : String
  def getRequestURI(): String
  def getContextPath() : String
  def getParameterNames() : Iterator[String]
  def getParameterValues(param: String) : Seq[String]
  def getHeaders(name: String) : Iterator[String]
}
