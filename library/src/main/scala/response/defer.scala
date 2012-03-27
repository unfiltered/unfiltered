package unfiltered.response

/** Defers evaluation of the given block until the response function
 * is applied. Applications may defer expensive compution, state changes,
 * blocking I/O, and other undesirable activity that would occur upon the
 * eager application of an intent. A Pass can not be deferred*/
object Defer {
  import unfiltered.response.{HttpResponse,ResponseFunction,Responder}
  def apply[A](rf: => ResponseFunction[A]) = new Responder[A] {
    def respond(res: HttpResponse[A]) { rf(res) }
  }
}
