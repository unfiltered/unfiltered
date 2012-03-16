package unfiltered.request

import java.io.{Reader, InputStream}
import unfiltered.Cookie

abstract class HttpRequest[+T](val underlying: T) {
  // T is covariant so e.g. a HttpRequest[HttpServletRequest] can be
  //  supplied when HttpRequest[Any] is expected.
  /** read-once access to request body input stream */
  def inputStream: InputStream
  /** buffered reader for request body's input stream */
  def reader: Reader
  /** The HTTP protocol version */
  def protocol: String
  /** HTTP verb in all caps */
  def method: String
  /** full HTTP request uri including raw query string http://tools.ietf.org/html/rfc2616#section-5.1.2 */
  def uri: String
  /** GET and POST parameter names */
  def parameterNames: Iterator[String]
  /** Sequence of values associated with a parameter. Nil if none */
  def parameterValues(param: String) : Seq[String]
  /** Iterator of request headers */
  def headers(name: String) : Iterator[String]
  /** parsed cookie string cookies */
  @deprecated("use the unfiltered.request.Cookies request extractor instead")
  def cookies: Seq[Cookie]
  /** true if the request is using tls, false otherwise */
  def isSecure: Boolean
  /** address associated with the source of the request */
  def remoteAddr: String
}
