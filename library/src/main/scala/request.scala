package unfiltered.request

import javax.servlet.ServletRequest
import javax.servlet.http.HttpServletRequest

object HTTPS {
  def unapply(req: HttpServletRequest) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}

class Method(method: String) {
  def unapply(req: HttpServletRequest) = 
    if (req.getMethod.equalsIgnoreCase(method)) Some(req)
    else None
}

object GET extends Method("GET")
object POST extends Method("POST")
object PUT extends Method("PUT")
object DELETE extends Method("DELETE")
object HEAD extends Method("HEAD")

object Path {
  def unapply(req: HttpServletRequest) = Some((req.getRequestURI, req))
}
object Seg {
  def unapply(path: String): Option[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}

class RequestHeader(name: String) {
  def unapplySeq(req: HttpServletRequest): Option[Seq[String]] = { 
    def headers(e: java.util.Enumeration[_]): List[String] =
      if (e.hasMoreElements) e.nextElement match {
        case v: String => v :: headers(e)
        case _ => headers(e)
      } else Nil
    Some(headers(req.getHeaders(name)))
  }
}

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object Accept extends RequestHeader("Accept")
object AcceptCharset extends RequestHeader("Accept-Charset")
object AcceptEncoding extends RequestHeader("Accept-Encoding")
object AcceptLanguage extends RequestHeader("Accept-Language")
object Authorization extends RequestHeader("Authorization")
object Connection extends RequestHeader("Connection")
object Expect extends RequestHeader("Expect")
object From extends RequestHeader("From")
object Host extends RequestHeader("Host")
object IfMatch extends RequestHeader("If-Match")
object IfModifiedSince extends RequestHeader("If-Modified-Since")
object IfNoneMatch extends RequestHeader("If-None-Match")
object IfRange extends RequestHeader("If-Range")
object IfUnmodifiedSince extends RequestHeader("If-Unmodified-Since")
object MaxForwards extends RequestHeader("Max-Forwards")
object ProxyAuthorization extends RequestHeader("Proxy-Authorization")
object Range extends RequestHeader("Range")
object Referer extends RequestHeader("Referer")
object TE extends RequestHeader("TE")
object Upgrade extends RequestHeader("Upgrade")
object UserAgent extends RequestHeader("User-Agent")
object Via extends RequestHeader("Via")

object InStream {
  def unapply(req: HttpServletRequest) = Some(req.getInputStream, req)
}

object Read {
  def unapply(req: HttpServletRequest) = Some(req.getReader, req)
}

object Bytes {
  def unapply(req: HttpServletRequest) = {
    val InStream(in, _) = req
    val bos = new java.io.ByteArrayOutputStream
    val ba = new Array[Byte](4096)
    /* @scala.annotation.tailrec */ def read {
      val len = in.read(ba)
      if (len > 0) bos.write(ba, 0, len)
      if (len >= 0) read
    }
    read
    in.close
    bos.toByteArray match {
      case Array() => None
      case ba => Some(ba, req)
    }
  }
}

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  /**
    Given a req, extract the request params into a (Map[String, Seq[String]], requset).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) => 
        m + (n -> req.getParameterValues(n))
      )).withDefaultValue(Nil), req)
  }

  abstract class Named[T](name: String, f: Seq[String] => Option[T]) {
    def unapply(params: Map[String, Seq[String]]) = f(params(name)) map {
      (_, params)
    }
  }

  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained( { seq: Seq[String] => seq.headOption } )

  val trimmed = { (_: Option[String]) map { _.trim } }
  val nonempty = { (_: Option[String]) filter { ! _.isEmpty  } }

  val int = { s: Option[String] =>
    try { s map { _.toInt } } catch { case _ => None }
  }
    
}