package unfiltered.directives

import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction
import unfiltered.Cycle
import annotation.implicitNotFound
import unfiltered.response.HttpResponse

object Directive {
  import Result.Success
  import Result.Failure
  import Result.Error

  def apply[T, R, A](run: HttpRequest[T] => Result[R, A]): Directive[T, R, A] =
    new Directive[T, R, A](run)

  trait Fail[-T, +R, +A] {
    def map[X](f: R => X): Directive[T, X, A]
    def ~>[RR, TT <: T, AA >: A](and: ResponseFunction[RR])(implicit
      ev: Fail[T, R, A] <:< Fail[TT, ResponseFunction[RR], AA]
    ): Directive[TT, ResponseFunction[RR], AA] = ev(this).map(_ ~> and)
  }

  object Intent {

    /** General directive intent constructor, for a partial function of requests */
    def apply[A, B](
      intent: PartialFunction[HttpRequest[A], HttpRequest[A] => Result[ResponseFunction[B], ResponseFunction[B]]]
    ): unfiltered.Cycle.Intent[A, B] = {
      case req if intent.isDefinedAt(req) =>
        intent(req)(req) match {
          case Success(response) => response
          case Failure(response) => response
          case Error(response) => response
        }
    }

    /** Directive intent constructor for a partial function of path strings  */
    def Path[T]: Mapping[T, String] = Mapping(unfiltered.request.Path[T])

    case class Mapping[T, X](from: HttpRequest[T] => X) {
      def apply[TT <: T, R](
        intent: PartialFunction[X, HttpRequest[TT] => Result[ResponseFunction[R], ResponseFunction[R]]]
      ): Cycle.Intent[TT, R] =
        Intent {
          case req if intent.isDefinedAt(from(req)) => intent(from(req))
        }
    }
  }

  @implicitNotFound("implicit instance of Directive.Eq[${X}, ${V}, ?, ?, ?] not found")
  case class Eq[-X, -V, -T, +R, +A](directive: (X, V) => Directive[T, R, A])

  @implicitNotFound("implicit instance of Directive.Gt[${X}, ${V}, ?, ?, ?] not found")
  case class Gt[-X, -V, -T, +R, +A](directive: (X, V) => Directive[T, R, A])

  @implicitNotFound("implicit instance of Directive.Lt[${X}, ${V}, ?, ?, ?] not found")
  case class Lt[-X, -V, -T, +R, +A](directive: (X, V) => Directive[T, R, A])
}

class Directive[-T, +R, +A](run: HttpRequest[T] => Result[R, A]) extends (HttpRequest[T] => Result[R, A]) {

  def apply(request: HttpRequest[T]): Result[R, A] = run(request)

  def map[TT <: T, RR >: R, B](f: A => B): Directive[TT, RR, B] =
    Directive(r => run(r).map(f))

  def flatMap[TT <: T, RR >: R, B](f: A => Directive[TT, RR, B]): Directive[TT, RR, B] =
    Directive(r => run(r).flatMap(a => f(a)(r)))

  def orElse[TT <: T, RR >: R, B >: A](next: => Directive[TT, RR, B]): FilterDirective[TT, RR, B] =
    new FilterDirective(r => run(r).orElse(next(r)), next)

  def |[TT <: T, RR >: R, B >: A](next: => Directive[TT, RR, B]): FilterDirective[TT, RR, B] =
    orElse(next)

  def and[TT <: T, E, B, RF](
    other: => Directive[TT, JoiningResponseFunction[E, RF], B]
  )(implicit ev: R <:< JoiningResponseFunction[E, RF]): FilterDirective[TT, JoiningResponseFunction[E, RF], (A, B)] = {
    val runner = (req: HttpRequest[TT]) => this(req) and other(req)
    // A `filter` implementation is required for pattern matching, which we
    // use to extract joined successes. This is a no-op filter; an improved
    // response joining system would make `toResponseFunction` available
    // to use here with an empty Seq.
    new FilterDirective[TT, JoiningResponseFunction[E, RF], (A, B)](
      runner,
      runner
    )
  }

  def &[TT <: T, E, B, RF](other: => Directive[TT, JoiningResponseFunction[E, RF], B])(implicit
    ev: R <:< JoiningResponseFunction[E, RF]
  ) = this and other

  def fail: Directive.Fail[T, R, A] = new Directive.Fail[T, R, A] {
    def map[B](f: R => B) =
      Directive(run(_).fail.map(f))
  }
}

/** Supports filtering by requiring a handler for when success is filtered away.
    The onEmpty handler may produce a success or failure; typically the latter. */
class FilterDirective[-T, +R, +A](
  run: HttpRequest[T] => Result[R, A],
  onEmpty: HttpRequest[T] => Result[R, A]
) extends Directive[T, R, A](run) {
  def filter(filt: A => Boolean): FilterDirective[T, R, A] = withFilter(filt)
  def withFilter(filt: A => Boolean): FilterDirective[T, R, A] =
    new FilterDirective(
      { req =>
        run(req).flatMap { a =>
          if (filt(a)) Result.Success(a)
          else onEmpty(req)
        }
      },
      onEmpty
    )
}

class JoiningResponseFunction[E, A](val elements: List[E], toResponseFunction: Seq[E] => ResponseFunction[A])
    extends ResponseFunction[A] {
  def apply[B <: A](res: unfiltered.response.HttpResponse[B]): HttpResponse[B] =
    toResponseFunction(elements)(res)

  def join(next: JoiningResponseFunction[E, A]) =
    new JoiningResponseFunction[E, A](elements ::: next.elements, toResponseFunction)
}

/** Convenience class, extend for a JoiningResponseFunction subclass */
class ResponseJoiner[E, A](element: E)(toResponseFunction: Seq[E] => ResponseFunction[A])
    extends JoiningResponseFunction[E, A](element :: Nil, toResponseFunction)
