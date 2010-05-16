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
  def unapply(req: HttpServletRequest) = Some((req, req.getRequestURI))
}
object Segs {
  def unapplySeq(req: HttpServletRequest): Option[(HttpServletRequest, Seq[String])] = 
    Some((req, req.getRequestURI.split("/").drop(1)))
}