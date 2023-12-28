package unfiltered.request

/**
 * AgentExtractor instances provide a predicate (HttpRequest[?] => Boolean)
 * for ad-hoc tests, a standard Unfiltered request extractor, and a means
 * for composing simple extractors into more complex ones.
 */
trait AgentExtractor extends (HttpRequest[?] => Boolean) {
  /** Predicate for an agent string. */
  val test: String => Boolean
  /** Composition (f.g). */
  def &(e: AgentExtractor) = new AgentExtractor {
    val test = (ua: String) => AgentExtractor.this.test(ua) && e.test(ua)
  }
  def apply(req: HttpRequest[?]) =
    UserAgent.unapply(req).exists(test)
  def unapply[A](req: HttpRequest[A]) =
    UserAgent.unapply(req).collect { case ua if test(ua) => Some(req) }
}
