package unfiltered.request

/**
 * AgentExtractor instances provide predicate (HttpRequest[_] => Boolean)
 * for ad-hoc tests, a standard Unfiltered reqeust extractor, and a means
 * for composing simple extractors into more complex ones.
 */
trait AgentExtractor extends (HttpRequest[_] => Boolean) {
  /** Predicate for an agent string. */
  val test: String => Boolean
  /** Composition (f.g). */
  def &(e: AgentExtractor) = new AgentExtractor {
    val test = (ua: String) => AgentExtractor.this.test(ua) && e.test(ua)
  }
  def apply(req: HttpRequest[_]) =
    UserAgent.unapply(req).map(test).getOrElse(false)
  def unapply[A](req: HttpRequest[A]) =
    UserAgent.unapply(req).flatMap(ua => if(test(ua)) Some(req) else None)
}
