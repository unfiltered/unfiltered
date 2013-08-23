package unfiltered.directives

import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction
import unfiltered.Cycle
import annotation.implicitNotFound

object Directive {
  import Result.{Success, Failure, Error}

  def apply[T, R, A](run:HttpRequest[T] => Result[R, A]):Directive[T, R, A] =
    new Directive[T, R, A](run)

  trait Fail[-T, +R, +A]{
    def map[X](f:R => X):Directive[T, X, A]
    def ~> [RR, TT <: T, AA >: A](and: ResponseFunction[RR])
                   (implicit ev: Fail[T,R,A] <:< Fail[TT,ResponseFunction[RR],AA])
        : Directive[TT, ResponseFunction[RR], AA] = ev(this).map(_ ~> and)
  }

  object Intent {
    /** General directive intent constructor, for a partial function of requests */
    def apply[A,B](
    intent: PartialFunction[HttpRequest[A],
                            (HttpRequest[A] => Result[ResponseFunction[B],
                                                      ResponseFunction[B]])]
    ): unfiltered.Cycle.Intent[A,B] = {
      case req if intent.isDefinedAt(req) => intent(req)(req) match {
        case Success(response) => response
        case Failure(response) => response
        case Error(response)   => response
      }
    }
    /** Directive intent constructor for a partial function of path strings  */
    def Path[T] = Mapping(unfiltered.request.Path[T])

    case class Mapping[T, X](from: HttpRequest[T] => X) {
      def apply[TT <: T, R](
        intent: PartialFunction[X, HttpRequest[TT] => Result[ResponseFunction[R],
                                                             ResponseFunction[R]]]
      ): Cycle.Intent[TT, R] =
        Intent {
          case req if intent.isDefinedAt(from(req)) => intent(from(req))
        }
    }
  }

  @implicitNotFound("implicit instance of Directive.Eq[${X}, ${V}, ?, ?, ?] not found")
  case class Eq[-X, -V, -T, +R, +A](directive:(X, V) => Directive[T, R, A])

  @implicitNotFound("implicit instance of Directive.Gt[${X}, ${V}, ?, ?, ?] not found")
  case class Gt[-X, -V, -T, +R, +A](directive:(X, V) => Directive[T, R, A])

  @implicitNotFound("implicit instance of Directive.Lt[${X}, ${V}, ?, ?, ?] not found")
  case class Lt[-X, -V, -T, +R, +A](directive:(X, V) => Directive[T, R, A])
}

class Directive[-T, +R, +A](run:HttpRequest[T] => Result[R, A])
extends (HttpRequest[T] => Result[R, A]) {

  def apply(request: HttpRequest[T]) = run(request)

  def map[TT <: T, RR >: R, B](f:A => B):Directive[TT, RR, B] =
    Directive(r => run(r).map(f))

  def flatMap[TT <: T, RR >: R, B](f:A => Directive[TT, RR, B]):Directive[TT, RR, B] =
    Directive(r => run(r).flatMap(a => f(a)(r)))

  /** Doesn't filter. Scala requires something to be defined for pattern matching in for
      expressions, and we do use that. */
  def withFilter(f:A => Boolean): Directive[T, R, A] = this

  def orElse[TT <: T, RR >: R, B >: A](next: => Directive[TT, RR, B]): Directive[TT, RR, B] =
    Directive(r => run(r).orElse(next(r)))

  def | [TT <: T, RR >: R, B >: A](next: => Directive[TT, RR, B]): Directive[TT, RR, B] =
    orElse(next)

  def and[TT <: T, E, B, RF](other: => Directive[TT, JoiningResponseFunction[E,RF], B])
    (implicit ev: R <:< JoiningResponseFunction[E,RF]) =
    new Directive[TT, JoiningResponseFunction[E,RF], (A,B)] ( req =>
      this(req) and other(req)
    )

  def &[TT <: T, E, B, RF](other: => Directive[TT, JoiningResponseFunction[E,RF], B])
    (implicit ev: R <:< JoiningResponseFunction[E,RF]) = this and other

  def fail: Directive.Fail[T, R, A] = new Directive.Fail[T, R, A] {
    def map[B](f: R => B) =
      Directive(run(_).fail.map(f))
  }
}

class JoiningResponseFunction[E,A]
(val elements: List[E], toResponseFunction: Seq[E] => ResponseFunction[A])
extends ResponseFunction[A] {
  def apply[B <: A](res: unfiltered.response.HttpResponse[B]) =
    toResponseFunction(elements)(res)

  def join(next: JoiningResponseFunction[E,A]) =
    new JoiningResponseFunction[E,A](elements ::: next.elements, toResponseFunction)
}

class ResponseJoiner[E,A](element: E)(toResponseFunction: Seq[E] => ResponseFunction[A])
extends JoiningResponseFunction[E,A](element :: Nil, toResponseFunction)

object & {
  def unapply[A,B](tup: Tuple2[A,B]) = Some(tup)
}
