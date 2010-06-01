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
object IfNoneMatch extends RequestHeader("If-None-Match")


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
  /** Convenience wrapper for parameter map */
  class Params(val pmap: Map[String, Seq[String]]) {
    def apply(name: String) = pmap.get(name)
    def first(name: String) = apply(name) match {
      case Some(values) => Some(values.first)
      case _ => None
    }
    def isDefinedAt(name: String) = pmap.isDefinedAt(name)
  }
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  /** Given a req, extract the request params into a (Params, req) tuple */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some((new Params((Map.empty[String, Seq[String]] /: names) ((m, n) => 
        m + (n -> req.getParameterValues(n))
      )), req))
  }
}