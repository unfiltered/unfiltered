package unfiltered.response

trait ResponseFunction[-A] { self =>
  // A is contravariant so e.g. a ResponseFunction[Any] can be supplied
  // when ResponseFunction[HttpServletResponse] is expected.
  def apply[B <: A](res: HttpResponse[B]): HttpResponse[B]

  /** Like Function1#andThen. Composes another response function around this one */
  def andThen[B <: A](that: ResponseFunction[B]): ResponseFunction[B] =
    ComposedResponseFunction(self, that)

  /** Symbolic alias for andThen */
  def ~>[B <: A](that: ResponseFunction[B]) = andThen(that)
}

/** Responders always return the provided instance of HttpResponse */
trait Responder[A] extends ResponseFunction[A] {
  def apply[B <: A](res: HttpResponse[B]) = {
    respond(res)
    res
  }
  def respond(res: HttpResponse[A]): Unit
}

/** Convenience base class for response function classes defined as a
  * constructor parameter. */
class ComposeResponse[A](rf: ResponseFunction[A]) extends 
    Responder[A] {
  def respond(res: HttpResponse[A]): Unit = { rf(res) }
}

/** Composes two response functions. As a case class it recognizes
  * equivalence, so that (a ~> b == a ~> b) */
private case class ComposedResponseFunction[F, G <: F]
(f: ResponseFunction[F], g: ResponseFunction[G]) extends ResponseFunction[G] {
    def apply[R <: G](res: HttpResponse[R]) = g(f(res))
}

