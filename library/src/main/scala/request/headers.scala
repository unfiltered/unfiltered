package unfiltered.request

trait DateParser extends (String => java.util.Date)

object DateFormatting {
  import java.text.SimpleDateFormat
  import java.util.{ Date, Locale, TimeZone }

  def format(date: Date) =
    new SimpleDateFormat("E, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH) {
      setTimeZone(TimeZone.getTimeZone("GMT"))
    }.format(date)

  def parseAs(fmt: String)(value: String): Option[Date] =
    try { Some(new SimpleDateFormat(fmt, Locale.US).parse(value)) }
    catch { case _ => None }

  /** Preferred HTTP date format Sun, 06 Nov 1994 08:49:37 GMT */
  def RFC1123 = parseAs("EEE, dd MMM yyyy HH:mm:ss z")_

  /** Sunday, 06-Nov-94 08:49:37 GMT */
  def RFC1036 = parseAs("EEEEEE, dd-MMM-yy HH:mm:ss z")_

  /** Sun Nov  6 08:49:37 1994 */
  def ANSICTime = parseAs("EEE MMM  d HH:mm:ss yyyy")_

  /** @return various date coersion formats falling back on None value */
  def parseDate(raw: String) = RFC1123(raw) orElse RFC1036(raw) orElse ANSICTime(raw)
}

/** A header with values mapped to keys in a Map. */
private [request] class MappedRequestHeader[A, B](val name: String)(parser: Iterator[String] => Map[A, B]) {
  def unapply[T](req: HttpRequest[T]) = parser(req.headers(name)) match {
    case hs => Some(hs)
  }
  def apply[T](req: HttpRequest[T]) = parser(req.headers(name))
}

/** A header with comma delimited values. Implementations of this extractor
 * will not match requests for which the header `name` is not present.*/
private [request] class SeqRequestHeader[T](val name: String)(parser: Iterator[String] => List[T]) {
  def unapply[T](req: HttpRequest[T]) = parser(req.headers(name)) match {
    case Nil => None
    case hs => Some(hs)
  }
  def apply[T](req: HttpRequest[T]) = parser(req.headers(name))
}

/** A header with a single value. Implementations of this extractor
 * will not match requests for which the header `name` is not present.*/
private [request] class RequestHeader[A](val name: String)(parser: Iterator[String] => List[A]) {
   def unapply[T](req: HttpRequest[T]) =  parser(req.headers(name)) match {
     case head :: _ => Some(head)
     case _ => None
   }
   def apply[T](req: HttpRequest[T]) = parser(req.headers(name)).headOption
}

private [request] object DateValueParser extends (Iterator[String] => List[java.util.Date]) {
  import DateFormatting._
  def apply(values: Iterator[String]) =
    values.toList.flatMap(parseDate)
}

private [request] object IntValueParser extends (Iterator[String] => List[Int]) {
   def tryInt(raw: String) = try { Some(raw.toInt) } catch { case _ => None }
   def apply(values: Iterator[String]) =
     values.toList.flatMap(tryInt)
}

private [request] object StringValueParser extends (Iterator[String] => List[String]) {
  def apply(values: Iterator[String]) =
    values.toList
}

private [request] object UriValueParser extends (Iterator[String] => List[java.net.URI]) {
  def toUri(raw: String) =
    try { Some(new java.net.URI(raw)) }
    catch { case _ => None }

  def apply(values: Iterator[String]) =
    values.toList.flatMap(toUri)
}

private [request] object SeqValueParser extends (Iterator[String] => List[String]) {
   def apply(values: Iterator[String]) = {
     def split(raw: String): List[String] =
       (raw.split(",") map {
         _.trim.takeWhile { _ != ';' } mkString
       }).toList
     values.toList.flatMap(split)
   }
}

/** Header whose value should be a date and time. Parsing is attempted
 * for formats defined in the DateFormatting object, in this order:
 * RFC1123, RFC1036,  ANSICTime. */
class DateHeader(name: String) extends RequestHeader(name)(DateValueParser)
/** A repeatable header may be specified in more than one header k-v pair and
 *  whose values are a list delimited by comma
 *  see also http://www.w3.org/Protocols/rfc2616/rfc2616-sec4.html#sec4.2 */
class RepeatableHeader(name: String) extends SeqRequestHeader(name)(SeqValueParser)
/** Header whose value should be a valid URI. */
class UriHeader(name: String) extends RequestHeader(name)(UriValueParser)
/** Header whose value can be any string. */
class StringHeader(name: String) extends RequestHeader(name)(StringValueParser)
/** Header whose value should be an integer. (Is stored in an Int.) */
class IntHeader(name: String) extends RequestHeader(name)(IntValueParser)

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object Accept extends RepeatableHeader("Accept")
object AcceptCharset extends RepeatableHeader("Accept-Charset")
object AcceptEncoding extends RepeatableHeader("Accept-Encoding")
object AcceptLanguage extends RepeatableHeader("Accept-Language")
object Authorization extends StringHeader("Authorization")
object Connection extends StringHeader("Connection")
// may want opt added parser i.e for charset
object RequestContentType extends StringHeader("Content-Type")
object Expect extends StringHeader("Expect")
object From extends StringHeader("From")
object Host extends StringHeader("Host")
object IfMatch extends RepeatableHeader("If-Match")
object IfModifiedSince extends DateHeader("If-Modified-Since")
object IfNoneMatch extends RepeatableHeader("If-None-Match")
object IfRange extends StringHeader("If-Range") // can also be an http date
object IfUnmodifiedSince extends DateHeader("If-Unmodified-Since")
object MaxForwards extends IntHeader("Max-Forwards")
object ProxyAuthorization extends StringHeader("Proxy-Authorization")
object Range extends RepeatableHeader("Range")// there more structure here
object Referer extends UriHeader("Referer")
object TE extends RepeatableHeader("TE")
object Upgrade extends RepeatableHeader("Upgrade")
object UserAgent extends StringHeader("User-Agent")// maybe a bit more structure here
object Via extends RepeatableHeader("Via")
object XForwardedFor extends RepeatableHeader("X-Forwarded-For")

/** Extracts the charset value from the Content-Type header, if present */
object Charset {
  val Setting = """.*;.*\bcharset=(\S+).*""".r
  def unapply[T](req: HttpRequest[T]) =
    req.headers(RequestContentType.name).toList.flatMap {
      case Setting(cs) => (cs, req) :: Nil
      case _ => Nil
    }.headOption
}

/** Extracts hostname and port separately from the Host header, setting
 * a default port of 80 or 443 when none is specified */
object HostPort {
  import unfiltered.util.Of
  def unapply[T](req: HttpRequest[T]): Option[(String, Int)] =
    req match {
      case Host(hostname) => hostname.split(':') match {
        case Array(host, Of.Int(port)) => Some(host, port)
        case _ => Some(hostname, if(req.isSecure) 443 else 80)
      }
      case _ => None
    }
}
