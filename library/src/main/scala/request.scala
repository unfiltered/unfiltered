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
object / {
  def unapply(path: String): Option[(String, String)] = path.split("/", 2) match {
    case Array("", a) => unapply(a) // skip a leading slash
    case Array(a, b) => Some((a, b))
    case _ => None
  }
}