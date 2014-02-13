package unfiltered.response
import unfiltered.request._

trait implicits {
  @inline implicit def upcast[A](baseRes: BaseResponseFunction[A]) = new ResponseFunction[A] {
    def apply[B <: A](res: HttpResponse[B]) = baseRes(res)
  }
}
trait BaseResponseFunction[-A] { self =>
  def apply[R <: BaseHttpResponse[A]](res: R): R

  def ~> [B <: A](that: BaseResponseFunction[B]) = new BaseResponseFunction[B] {
    def apply[R <: BaseHttpResponse[B]](res: R) = that(self(res))
  }
  def ~> [B <: A](that: ResponseFunction[B]) = new ResponseFunction[B] {
    def apply[C <: B](res: HttpResponse[C]) = that(self(res))
  }
}
trait ResponseFunction[-A] { self =>
  // A is contravariant so e.g. a ResponseFunction[Any] can be supplied
  // when ResponseFunction[HttpServletResponse] is expected.
  def apply[B <: A](res: HttpResponse[B]): HttpResponse[B]

  /** Like andThen, composes another response function around this one */
  def ~> [B <: A](that: ResponseFunction[B]) = new ResponseFunction[B] {
    def apply[C <: B](res: HttpResponse[C]) = that(self(res))
  }
  def ~> [B <: A](that: BaseResponseFunction[B]) = new ResponseFunction[B] {
    def apply[C <: B](res: HttpResponse[C]) = that(self(res))
  }
}

trait BaseResponder[A] extends BaseResponseFunction[A] {
  def apply[R <: BaseHttpResponse[A]](res: R): R = {
    respond(res)
    res
  }
  def respond(res: BaseHttpResponse[A])
}
/** Responders always return the provided instance of HttpResponse */
trait Responder[A] extends ResponseFunction[A] {
  def apply[B <: A](res: HttpResponse[B]) = {
    respond(res)
    res
  }
  def respond(res: HttpResponse[A])
}

/** Base class for composing a response function from others */
class ComposeResponse[A](rf: ResponseFunction[A]) extends 
    Responder[A] {
  def respond(res: HttpResponse[A]) { rf(res) }
}
