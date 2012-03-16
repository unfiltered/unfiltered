package unfiltered.request

import unfiltered.Cookie

/** An implementation of a function used to map cookie names to their value as an Option. If there
 *  is no associated Cookie value. None will always be returned. */
private [request] object CookieValueParser extends (Iterator[String] => Map[String, Option[Cookie]]) {
  def apply(values: Iterator[String]) = {
    val vs = values.toList
    ((Map.empty[String, Option[Cookie]] /: vs.flatMap(FromCookies.apply _))(
      (m, c) => m + (c.name -> Some(c))
    ).withDefaultValue(None))
  }
}

/** Primary Cookie extractor used for obtaining a collection cookies mapped
 *  to their names from the HTTP `Cookie` header */
object Cookies extends MappedRequestHeader[String, Option[Cookie]]("Cookie")(CookieValueParser)

/** Module for Cookie deserialization.
 * Some optional cookie properties defined in http://tools.ietf.org/html/rfc2965 are not included in this implementation's 
 * deserialized cookie representation. This list includes `Comment`, `CommentURL`, `Discard`, and `Port` */
object FromCookies {
  import unfiltered.CookieKeys._
  val Cutter = "(?:\\s|[;,])*\\$*([^;=]+)(?:=(?:[\"']((?:\\\\.|[^\"])*)[\"']|([^;,]*)))?(\\s*(?:[;,]+\\s*|$))".r

  def apply(cstr: String): Seq[Cookie] = {
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
                         (names, values, Some((n0, v0 + s0 + name, sep)))
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
        case _ => /*sys.*/error("Error parsing cookie string %s" format cstr)
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
        // baking the cookies.
        // Given a seq of names and a seq of values
        // iterate by one value, i, which creates a base cookie using a name, value combo 
        // then fold over the rest of the names & values building up the cookie
        // along with a flag that indicates of the a cookie has been build.
        // On each application of the fold, move the top level iterator's cursor
        // forword until a cookie is complete. We keep track of the completion flag
        // as a member of the fold to a) avoid introducing a mutable var, and b)
        // scala does not have a way to break from a for comprehention while still returning
        // a value. scala.util.control.Breakable is not an option here
        val iter: Iterator[Int] = (startAt until names.size).iterator
        (for(i <- iter) yield {
          ((false, Cookie(names(i), Option(values(i)).getOrElse(""))) /: (
            i + 1 until names.size)) { (a, j) =>
            val (complete, cookie) = a
            if(complete) a
            else (names(j).toLowerCase, values(j)) match {
              case (LCDiscard,    _) => iter.next; (false, cookie) // don't support
              case (LCSecure,     v) => iter.next; (false, cookie.copy(secure = Option(true)))
              case (LCHTTPOnly,   v) => iter.next; (false, cookie.copy(httpOnly = true))
              case (LCComment,    _) => iter.next; (false, cookie) // don't support 
              case (LCCommentURL, _) => iter.next; (false, cookie) // don't support
              case (LCDomain,     v) => iter.next; (false, cookie.copy(domain = Option(v)))
              case (LCPath,       v) => iter.next; (false, cookie.copy(path = Option(v)))
              case (LCExpires,    v) => iter.next; (false, try {
                val maxMils =
                  unfiltered.request.DateFormatting.parseDate(v).get.getTime -
                    System.currentTimeMillis
                cookie.copy(maxAge = Option(
                  if(maxMils < 1)  0
                  else (maxMils / 1000).toInt + (if(maxMils % 1000 != 0) 1 else 0)))
              } catch {
                case _ => cookie
              })
              case (LCMaxAge, v)     => iter.next;  (false, try {
                cookie.copy(maxAge = Option(Integer.parseInt(v)))
              } catch {
                case _ => cookie
              })
              case (LCVersion, v)    => iter.next;  (false, try {
                cookie.copy(version = Integer.parseInt(v))
              } catch {
                case _ => cookie
              })
              case (LCPort, _)    =>  iter.next; (false, cookie) // don't support
              case (pass, _)      =>  (true, cookie)
            }
          }
        })
        // discard completion statuses
        .map(_._2).toSeq
      }
    }
  }
}

