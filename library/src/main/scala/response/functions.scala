package unfiltered.response
import unfiltered.request._

trait ResponseFunction[-A] { 
  // A is contravariant so e.g. a ResponseFunction[Any] can be supplied
  // when ResponseFunction[HttpServletResponse] is expected.
  def apply[B <: A](res: HttpResponse[B]): HttpResponse[B]
}

trait SubtypeResponder[A] extends ResponseFunction[A] { self =>
  def apply[B <: A](res: HttpResponse[B]) = {
    respond(res)
    res
  }
  def respond(res: HttpResponse[A])
  def ~> [B <: A](that: ResponseFunction[B]) = new SubtypeResponder[B] {
    def respond(res: HttpResponse[B]) {
      that(self(res))
    }
  }
}
/** The typical Responder does not depend on any underlying type */
trait Responder extends SubtypeResponder[Any]

/** Base class for composing a response function from others */
class ChainResponse[A](f: ResponseFunction[A]) extends 
    SubtypeResponder[A] {
  def respond(res: HttpResponse[A]) { f(res) }
}

/** Tells the binding implentation to treat the request as non-matching */
object Pass extends ResponseFunction[Any] {
  def apply[T](res: HttpResponse[T]) = res
}
