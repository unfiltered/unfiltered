package unfiltered.request

private [request] object StringValueParser extends (Iterator[String] => List[String]) {
  def apply(values: Iterator[String]) =
    values.toList
}

/** A header with a single value. Implementations of this extractor
 * will not match requests for which the header `name` is not present.*/
private [request] class RequestHeader[A](val name: String)(parser: Iterator[String] => List[A]) extends RequestExtractor[A] {
   def unapply[T](req: HttpRequest[T]) = parser(req.headers(name)).headOption
   def apply[T](req: HttpRequest[T]) = parser(req.headers(name)).headOption
}

/** Header whose value can be any string. */
class StringHeader(name: String) extends RequestHeader(name)(StringValueParser)

object RequestContentType extends StringHeader("Content-Type")

/** Extracts the charset value from the Content-Type header, if present */
object Charset {
  import unfiltered.util.MIMEType
  def unapply[T](req: HttpRequest[T]) = {
    for {
      MIMEType(mimeType) <- RequestContentType(req)
      charset <- mimeType.params.get("charset")
    } yield charset
  }
  def apply[T](req: HttpRequest[T]) = unapply(req)
}
