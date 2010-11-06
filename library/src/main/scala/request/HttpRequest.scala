package unfiltered.request

import java.io.{Reader, InputStream}
import unfiltered.Cookie

abstract class HttpRequest[T](val underlying: T) {
  def inputStream: InputStream
  def reader: Reader
  def protocol: String
  def method: String
  def requestURI: String
  def contextPath: String
  def parameterNames: Iterator[String]
  def parameterValues(param: String) : Seq[String]
  def headers(name: String) : Iterator[String]
  def cookies: Seq[Cookie]
  def isSecure: Boolean
  def remoteAddr: String
}
