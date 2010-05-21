package unfiltered.header
import javax.servlet.http.{HttpServletResponse, HttpServletRequest}

case class Header(name: String, values: Iterable[String]) extends unfiltered.response.Responder {
  def respond(res: HttpServletResponse) { 
    values.foreach { v => res.addHeader(name, v) } 
  }
}
case class HeaderName(name: String) {
  def unapplySeq(req: HttpServletRequest): Option[Seq[String]] = { 
    def headers(e: java.util.Enumeration[_]): List[String] =
      if (e.hasMoreElements) e.nextElement match {
        case v: String => v :: headers(e)
        case _ => headers(e)
      } else Nil
    Some(headers(req.getHeaders(name)))
  }
  def apply(value: String*) = Header(name, value)
}

object ETag extends HeaderName("ETag")
object CacheControl extends HeaderName("Cache-Control")
object IfNoneMatch extends HeaderName("If-None-Match")

