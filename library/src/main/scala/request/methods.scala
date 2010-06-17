package unfiltered.request

import javax.servlet.http.HttpServletRequest


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
