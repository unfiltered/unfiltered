package unfiltered.response
import unfiltered.request._

trait ResponseFunction[-A] { self =>
  // A is contravariant so e.g. a ResponseFunction[Any] can be supplied
  // when ResponseFunction[HttpServletResponse] is expected.
  def apply[B <: A](res: HttpResponse[B]): HttpResponse[B]

  /** Like andThen, composes another response function around this one */
  def ~> [B <: A](that: ResponseFunction[B]) = new ResponseFunction[B] {
    def apply[C <: B](res: HttpResponse[C]) = that(self(res))
  }
}

/** Responders always return the provided instance of HttpResponse */
trait Responder[A] extends ResponseFunction[A] {
  def apply[B <: A](res: HttpResponse[B]) = {
    respond(res)
    res
  }
  def respond(res: HttpResponse[A])
}

@deprecated("Use ComposeResponse")
class ChainResponse[A](f: ResponseFunction[A]) extends ComposeResponse(f)

/** Base class for composing a response function from others */
class ComposeResponse[A](rf: ResponseFunction[A]) extends 
    Responder[A] {
  def respond(res: HttpResponse[A]) { rf(res) }
}
