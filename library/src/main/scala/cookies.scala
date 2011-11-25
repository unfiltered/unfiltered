package unfiltered

/** See also http://tools.ietf.org/html/rfc2965#page-5 */
object CookieKeys {
  val Path = "Path"
  val Expires = "Expires"
  val MaxAge = "Max-Age"
  val Domain = "Domain"
  val Secure = "Secure"
  val HTTPOnly = "HTTPOnly"
  val Comment = "Comment"
  val CommentURL = "CommentURL"
  val Discard = "Discard"
  val Port = "Port"
  val Version = "Version"

  val LCPath = Path.toLowerCase
  val LCExpires = Expires.toLowerCase
  val LCMaxAge = MaxAge.toLowerCase
  val LCDomain = Domain.toLowerCase
  val LCSecure = Secure.toLowerCase
  val LCHTTPOnly = HTTPOnly.toLowerCase
  val LCComment = Comment.toLowerCase
  val LCCommentURL = CommentURL.toLowerCase
  val LCDiscard = Discard.toLowerCase
  val LCPort = Port.toLowerCase
  val LCVersion = Version.toLowerCase

  /** Properties that do not have an associated value */
  val KeyOnly = Seq(Discard, Secure, HTTPOnly)
}

trait FromCookies {
  import CookieKeys._
  val Cutter = "(?:\\s|[;,])*\\$*([^;=]+)(?:=(?:[\"']((?:\\\\.|[^\"])*)[\"']|([^;,]*)))?(\\s*(?:[;,]+\\s*|$))".r

  def fromCookieString(cstr: String): Seq[Cookie] = {
    val (names, values) =
      (((List.empty[String], List.empty[String], (None: Option[(String, String, String)])) /:
         Cutter.findAllIn(cstr)) {
           (a, e) => (a, e) match {
             case ((names, values, prev), matchresult) =>
               matchresult match {
                 case Cutter(cname, couldBeValue, cvalue, delim) =>

                   val (name, value, sep) = (cname, cvalue match {
                     case null => couldBeValue match {
                       case null => cvalue
                       case is => is.replace("\\\"", "\"").replace("\\\\", "\\")
                     }
                     case v => v
                   }, delim)

                   prev match {
                     case None =>
                       (names, values, Some((name, Option(value).getOrElse(""), sep)))
                     case Some((n0, v0, s0)) =>
                       if(value == null &&
                          KeyOnly.filter(_.equalsIgnoreCase(name)).isEmpty) {
                         (names, values, Some((n0, v0 + sep + name, sep)))
                       } else {
                         (n0 :: names, v0 :: values, Some((name, value, delim)))
                       }
                   }
               }
             }
         }) match {
        case (ns, vs, Some((n, v, _))) =>
          if(n == null) (ns.reverse, vs.reverse)
          else ((n :: ns).reverse, (v :: vs).reverse)
        case _ => error("Error parsing cookie string %s" format cstr)
      }

    if(names.isEmpty) { Seq.empty[Cookie] }
    else {
      // version may appear before name-value
      val (version, startAt) =
        if(names(0).equalsIgnoreCase(Version))
          try { (Integer.parseInt(values(0)), 1) } catch { case _ => (0, 1) }
        else (0, 0)

      if(names.isEmpty) Seq.empty[Cookie]
      else {
        // bake the cookies
        val iter: Iterator[Int] = (startAt until names.size).iterator
        (Map.empty[String, Cookie] /:(
          for(i <- iter;
              j <- (i + 1 until names.size).iterator) yield (i, j))) {
            (a, e) =>
              val name = names(e._1)
              val c = a.get(name).getOrElse(Cookie(name, Option(values(e._1)).getOrElse("")))
              a updated (name, (names(e._2).toLowerCase, values(e._2)) match {
                case (LCDiscard, _)    => iter.next; c // don't support
                case (LCSecure, v)     => iter.next; c.copy(secure = Option(true))
                case (LCHTTPOnly, v)   => iter.next; c.copy(httpOnly = true)
                case (LCComment, _)    => iter.next; c // don't support 
                case (LCCommentURL, _) => iter.next; c // don't support
                case (LCDomain, v)     => iter.next; c.copy(domain = Option(v))
                case (LCPath, v)       => iter.next;c.copy(path = Option(v))
                case (LCExpires, v)    => iter.next;try {
                  val maxMils =
                    request.DateFormatting.parseDate(v).get.getTime -
                      System.currentTimeMillis
                  c.copy(maxAge = Option(
                    if(maxMils < 1)  0
                    else (maxMils / 1000).toInt + (if(maxMils % 1000 != 0) 1 else 0)))
                } catch {
                  case _ => c
                }
                case (LCMaxAge, v)     =>  iter.next; try {
                  c.copy(maxAge = Option(Integer.parseInt(v)))
                } catch {
                  case _ => c
                }
                case (LCVersion, v)    => iter.next; try {
                  c.copy(version = Integer.parseInt(v))
                } catch {
                  case _ => c
                }
                case (LCPort, _)       => iter.next;  c // don't support
                case (unknown, _)      =>  c
              }
            )
          }.values toSeq
      }
    }
  }
}

trait ToCookies {
  import CookieKeys._
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
            literal(Expires, request.DataFormatting.formatter.format(
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

// roughly based on netty's cookie enc/dec 
// but using in a more fp style
object Cookie extends ToCookies with FromCookies

case class Cookie(name: String, value: String, domain: Option[String] = None,
                  path: Option[String] = None, maxAge: Option[Int] = None,
                  secure: Option[Boolean] = None, httpOnly: Boolean = true, version: Int = 1) {
  @deprecated("use copy(domain = Some(d))")
  def domain(d: String): Cookie = copy(domain = Some(d))
  @deprecated("use copy(path=Some(p))")
  def path(p: String): Cookie = copy(path = Some(p))
  @deprecated("use copy(maxAge = Some(a))")
  def maxAge(a: Int): Cookie = copy(maxAge = Some(a))
  @deprecated("use copy(secure = Some(s))")
  def secure(s: Boolean): Cookie = copy(secure = Some(s))
}
