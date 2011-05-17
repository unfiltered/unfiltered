package unfiltered.request

trait DateParser extends (String => java.util.Date)

private [request] object DateFormatting {
  import java.text.SimpleDateFormat
  import java.util.Date

  def parseAs(fmt: String)(value: String): Option[Date] =
    try { Some(new SimpleDateFormat(fmt).parse(value)) }
    catch { case _ => None }

  /** Preferred HTTP date format Sun, 06 Nov 1994 08:49:37 GMT */
  def RFC1123 = parseAs("EEE, dd MMM yyyy HH:mm:ss z")_

  /** Sunday, 06-Nov-94 08:49:37 GMT */
  def RFC1036 = parseAs("EEEEEE, dd-MMM-yy HH:mm:ss z")_

  /** Sun Nov  6 08:49:37 1994 */
  def ANSICTime = parseAs("EEE MMM  d HH:mm:ss yyyy")_

  def parseDate(raw: String) = RFC1123(raw) orElse RFC1036(raw) orElse ANSICTime(raw)
}

/** a header with a seq of values */
class SeqRequestHeader[T](val name: String)(parser: Iterator[String] => List[T]) {
  def unapply[T](req: HttpRequest[T]) = parser(req.headers(name)) match {
    case Nil => None
    case hs => Some(hs)
  }
  def apply[T](req: HttpRequest[T]) = parser(req.headers(name))
}

/** a header with a single value */
class RequestHeader[A](val name: String)(parser: Iterator[String] => List[A]) {
   def unapply[T](req: HttpRequest[T]) =  parser(req.headers(name)) match {
     case head :: _ => Some(head)
     case _ => None
   }
   def apply[T](req: HttpRequest[T]) = parser(req.headers(name)).headOption
}

object DateValueParser extends (Iterator[String] => List[java.util.Date]) {
  import DateFormatting._
  def apply(values: Iterator[String]) =
    List.fromIterator(values).flatMap(parseDate)
}

object IntValueParser extends (Iterator[String] => List[Int]) {
   def tryInt(raw: String) = try { Some(raw.toInt) } catch { case _ => None }
   def apply(values: Iterator[String]) =
     List.fromIterator(values).flatMap(tryInt)
}

object StringValueParser extends (Iterator[String] => List[String]) {
  def apply(values: Iterator[String]) =
    List.fromIterator(values)
}

object UriValueParser extends (Iterator[String] => List[java.net.URI]) {
  def toUri(raw: String) =
    try { Some(new java.net.URI(raw)) }
    catch { case _ => None }

  def apply(values: Iterator[String]) =
    List.fromIterator(values).flatMap(toUri)
}

object SeqValueParser extends (Iterator[String] => List[String]) {
   def apply(values: Iterator[String]) = {
     def split(raw: String): List[String] =
       (raw.split(",") map {
         _.trim.takeWhile { _ != ';' } mkString
       }).toList
     List.fromIterator(values).flatMap(split)
   }
}

class DateParsedHeader(name: String) extends RequestHeader(name)(DateValueParser)
class SeqParsedHeader(name: String) extends SeqRequestHeader(name)(SeqValueParser)
class UriParsedHeader(name: String) extends RequestHeader(name)(UriValueParser)
class StringParsedHeader(name: String) extends RequestHeader(name)(StringValueParser)
class IntParsedHeader(name: String) extends RequestHeader(name)(IntValueParser)

// http://www.w3.org/Protocols/rfc2616/rfc2616-sec14.html#sec14.10

object Accept extends SeqParsedHeader("Accept")
object AcceptCharset extends SeqParsedHeader("Accept-Charset")
object AcceptEncoding extends SeqParsedHeader("Accept-Encoding")
object AcceptLanguage extends SeqParsedHeader("Accept-Language")
object Authorization extends StringParsedHeader("Authorization")
object Connection extends StringParsedHeader("Connection")
// may want opt added parser i.e for charset
object RequestContentType extends StringParsedHeader("Content-Type")
object Expect extends StringParsedHeader("Expect")
object From extends StringParsedHeader("From")
object Host extends StringParsedHeader("Host")
object IfMatch extends SeqParsedHeader("If-Match")
object IfModifiedSince extends DateParsedHeader("If-Modified-Since")
object IfNoneMatch extends SeqParsedHeader("If-None-Match")
object IfRange extends SeqParsedHeader("If-Range") // can also be an http date
object IfUnmodifiedSince extends DateParsedHeader("If-Unmodified-Since")
object MaxForwards extends IntParsedHeader("Max-Forwards")
object ProxyAuthorization extends StringParsedHeader("Proxy-Authorization")
object Range extends SeqParsedHeader("Range")// there more structure here
object Referer extends UriParsedHeader("Referer")
object TE extends SeqParsedHeader("TE")
object Upgrade extends SeqParsedHeader("Upgrade")
object UserAgent extends StringParsedHeader("User-Agent")// maybe a bit more structure here
object Via extends SeqParsedHeader("Via")
object XForwardedFor extends SeqParsedHeader("X-Forwarded-For")

object Charset {
  val Setting = """.*;.*\bcharset=(\S+).*""".r
  def unapply[T](req: HttpRequest[T]) =
    List.fromIterator(req.headers(RequestContentType.name)).flatMap {
      case Setting(cs) => (cs, req) :: Nil
      case _ => Nil
    }.firstOption
}

object HostPort {
  val Port = """^\S+[:](\d{4})$""".r
  def unapply[T](req: HttpRequest[T]): Option[(String, Int)] =
    req match {
      case Host(hostname) => hostname match {
        case Port(port) => Some(hostname, port.toInt)
        case _ => Some(hostname, if(req.isSecure) 443 else 80)
      }
    }
}
