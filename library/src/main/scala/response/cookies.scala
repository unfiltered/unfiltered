package unfiltered.response

import unfiltered.Cookie

//case class ResponseCookies(cookies: Cookie*) extends Responder[Any] {
//  def respond(res: HttpResponse[Any]) = res.cookies(cookies)
//}

/** Cookie response header using custom cookie deserializer
 * */
object ResponseCookies {
  def apply(cookies: Cookie*) =
    new ResponseHeader("Set-Cookie",
                       ToCookies.toCookieString(cookies:_*) :: Nil)
}


/** Module for Cookie serialization */
object ToCookies {
  import unfiltered.CookieKeys._
  val Quotables = Array('\t', ' ', '"', '(', ')', ',', '/', ':', ';', '<',
                        '=', '>', '?', '@', '[', '\\', ']', '{', '}')
  def quoted(k: String, v: String) =
    """%s="%s";""" format(k, v match {
      case null => ""
      case value => value.replace("\\", "\\\\").replace("\"", "\\\"")
    })

  def literal(k: String, v: String) = "%s=%s;" format(k, v)

  def add(k: String, v: String) = v match {
    case null => quoted(k, v)
    case value =>
      if(value.find(Quotables.contains).isDefined) quoted(k, v)
      else literal(k, v)
  }

  def append(sb: StringBuilder, c: Cookie) = {
    sb.append(add(c.name, c.value))
    c.maxAge match {
      case Some(ma) =>
        sb.append(c.version match {
          case v if(v >= 0) =>
            literal(Expires, unfiltered.request.DateFormatting.format(
              new java.util.Date(System.currentTimeMillis() + ma * 1000L)
            ))
          case _ =>
            add(MaxAge, ma.toString)
        })
      case _ => ()
    }
    c.path match {
      case Some(p) =>
        sb.append(c.version match {
          case v if(v > 0) =>
            add(Path, p)
          case _ =>
            literal(Path, p)
        })
      case _ => ()
    }
    c.domain match {
      case Some(d) =>
        sb.append(c.version match {
          case v if(v > 0) =>
            add(Domain, d)
          case _ =>
            literal(Domain, d)
        })
      case _ => ()
    }
    c.secure match {
      case Some(s) if(s) =>
        sb.append("%s;" format Secure)
      case _ => ()
    }
    if(c.httpOnly) sb.append("%s;" format HTTPOnly)
    // ignore v1 extras for now
  }

  def toCookieString(cs: Cookie*): String =
    (new StringBuilder /: cs) { (b, c) => append(b, c); b } toString

}
