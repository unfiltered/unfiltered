package unfiltered.request

object & {
  /** Used to bind extractors to a single request object */
  def unapply[A](a: A) = Some(a, a)
  /** Used to extract nested tuples produced by directives anded together */
  def unapply[A,B](tup: Tuple2[A,B]) = Some(tup)
}
