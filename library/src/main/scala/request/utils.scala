package unfiltered.request

import scala.language.reflectiveCalls

object & {
  /** Used to bind extractors to a single request object */
  def unapply[A](a: A) = Some(a, a)
  /** Used to extract nested tuples produced by directives anded together */
  def unapply[A,B](tup: Tuple2[A,B]) = Some(tup)
}

/** For working with request extractor objects */
object RequestExtractor {
  /** structural type for request extractors */
  type RE[E] = {
    def unapply[T](req: HttpRequest[T]): Option[E]
  }

  /** @return new extractor, reproduces request when predicate is satisfied */
  def predicate[E](reqExtract: RE[E])(predicate: E => Boolean) = new {
    def unapply[T](req: HttpRequest[T]): Option[HttpRequest[T]] =
      for (value <- reqExtract.unapply(req) if predicate(value))
      yield req
  }
}
