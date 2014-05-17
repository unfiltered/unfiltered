package unfiltered.request

import scala.language.reflectiveCalls

object & {
  /** Used to bind extractors to a single request object */
  def unapply[A](a: A) = Some(a, a)
  /** Used to extract nested tuples produced by directives anded together */
  def unapply[A,B](tup: Tuple2[A,B]) = Some(tup)
}

abstract class RequestExtractor[E] {
  def unapply[T](req: HttpRequest[T]): Option[E]
}

/** For working with request extractor objects */
object RequestExtractor {

  /** @return new extractor, reproduces request when predicate is satisfied */
  def predicate[E](reqExtract: RequestExtractor[E])(predicate: E => Boolean): Predicate[E] =
    new Predicate(reqExtract, predicate)

  final class Predicate[E] private[RequestExtractor] (reqExtract: RequestExtractor[E], predicate: E => Boolean) {
    def unapply[T](req: HttpRequest[T]): Option[HttpRequest[T]] =
      for (value <- reqExtract.unapply(req) if predicate(value))
      yield req
  }
}
