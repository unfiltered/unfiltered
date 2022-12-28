package unfiltered.response

import unfiltered.Cookie

/** Set-Cookie response header */
object SetCookies {
  private[this] val Name = "Set-Cookie"
  def apply(cookies: Cookie*) =
    ResponseHeader(Name, cookies.foldLeft(Seq.empty[String])(
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

  private final val QuotableRegex = java.util.regex.Pattern.compile("""[\t "\(\),/:;<=>?@\[\\\]{}]""")

  def apply(cs: Cookie*): String = {
    val res = new StringBuilder
    cs.foreach(append(res, _))
    if (res.nonEmpty) {
      res.setLength(res.length - 1)
      res.toString
    } else res.toString
  }

  private def escape(s: String) = s match {
    case null => ""
    case value => value.replace("\\", "\\\\").replace("\"", "\\\"")
  }

  private def quoted(k: String, v: String) =  k + "=\"" + escape(v) + "\";"

  private def literal(k: String, v: String) = s"${k}=${v};"

  private def add(k: String, v: String) = v match {
    case null => quoted(k, v)
    case value =>
      if(QuotableRegex.matcher(value).find()) quoted(k, v)
      else literal(k, v)
  }

  private def gmt(secs: Int) =
    DateFormatting.format(
      new Date(System.currentTimeMillis() + secs * 1000L)
    )

  private def append(sb: StringBuilder, c: Cookie) : Unit = {
    sb.append(add(c.name, c.value))
    c.maxAge foreach { ma =>
      sb.append(
        if(c.version == 0) literal(Expires, gmt(ma))
        else add(MaxAge, ma.toString)
      )
    }
    c.path foreach { p =>
      sb.append(
        if(c.version > 0) add(Path, p)
        else literal(Path, p)
      )
    }
    c.domain foreach { d =>
      sb.append(
        if(c.version > 0) add(Domain, d)
        else literal(Domain, d)
      )
    }
    if (c.secure.getOrElse(false)) sb.append("%s;" format Secure)
    if(c.httpOnly) sb.append("%s;" format HTTPOnly)
    if (c.version > 0) {
       sb.append(add(Version, c.version.toString))
    }
    if(sb.isEmpty) sb.setLength(sb.length - 1)
    // ignore v1 extras for now
  }
}
