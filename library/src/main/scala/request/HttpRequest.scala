package unfiltered.request

import java.io.{Reader, InputStream}

abstract class HttpRequest[T](val underlying: T) {
  def getInputStream() : InputStream
  def getReader() : Reader
  def getProtocol() : String
  def getMethod() : String
  def getRequestURI(): String
  def getContextPath() : String
  def getParameterNames() : java.util.Enumeration[String]
  def getParameterValues(param: String) : Array[String]
  def getHeaders(name: String) : java.util.Enumeration[String]
}
