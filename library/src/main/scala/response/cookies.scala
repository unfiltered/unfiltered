package unfiltered.response

import unfiltered.Cookie

/** Set-Cookie response header. See SetCookies */
object ResponseCookies {
  private val Name = "Set-Cookie"
  @deprecated("Use unfiltered.response.SetCookies(cookies) instead")
  def apply(cookies: Cookie*) =
    new ResponseHeader(Name, (Seq.empty[String] /: cookies)(
      (a, e) => ToCookies(e) +: a)
    )
}

/** Set-Cookie response header */
object SetCookies {
  private val Name = "Set-Cookie"
  def apply(cookies: Cookie*) =
    new ResponseHeader(Name, (Seq.empty[String] /: cookies)(
      (a, e) => ToCookies(e) +: a)
    )
  /** Call this method with a list of names to discard cookies */
  def discarding(names: String*) =
    apply(names.map(Cookie(_, "", maxAge = Some(0))):_*)
}

/** Module for Cookie serialization */
object ToCookies {
  import java.util.Date
  import unfiltered.CookieKeys._
  import unfiltered.request.DateFormatting

  private val Quotables = Array(
    '\t', ' ', '"', '(', ')', ',', '/', ':', ';', '<',
    '=', '>', '?', '@', '[', '\\', ']', '{', '}')

  def apply(cs: Cookie*): String =
    ((new StringBuilder /: cs) { (b, c) => append(b, c); b }) match {
      case sb if(!sb.isEmpty) =>
        sb.setLength(sb.length - 1)
        sb.toString
      case empty => empty.toString
    }

  private def quoted(k: String, v: String) =
    """%s="%s";""" format(k, v match {
      case null => ""
      case value => value.replace("\\", "\\\\").replace("\"", "\\\"")
    })

  private def literal(k: String, v: String) = "%s=%s;" format(k, v)

  private def add(k: String, v: String) = v match {
    case null => quoted(k, v)
    case value =>
      if(value.find(Quotables.contains).isDefined) quoted(k, v)
      else literal(k, v)
  }

  private def append(sb: StringBuilder, c: Cookie) = {
    sb.append(add(c.name, c.value))
    c.maxAge match {
      case Some(ma) =>
        sb.append(c.version match {
          case v if(v == 0) =>
            literal(Expires, DateFormatting.format(
              new Date(System.currentTimeMillis() + ma * 1000L)
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
    if (c.version > 0) {
       sb.append(add(Version, c.version.toString))
    }
    if(sb.isEmpty) sb.setLength(sb.length - 1)
    // ignore v1 extras for now
  }
}
