package unfiltered.request

trait AgentExtractor extends (HttpRequest[_] => Boolean) {
  val test: String => Boolean
  def &(e: AgentExtractor) = new AgentExtractor {
    val test: String => Boolean = (ua) => AgentExtractor.this.test(ua) && e.test(ua)
  }
  def apply(req: HttpRequest[_]) =
    UserAgent.unapply(req).map(test).getOrElse(false)
  def unapply[A](req: HttpRequest[A]) =
    UserAgent.unapply(req)
      .flatMap(ua => if(test(ua)) Some(req) else None)
}
