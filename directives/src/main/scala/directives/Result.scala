package unfiltered.directives

object Result {
  case class Success[+A](value:A) extends Result[Nothing, A]

  case class Failure[+R](response:R) extends Result[R, Nothing]

  case class Error[+R](response: R) extends Result[R, Nothing]

  trait FailResult[+R, +A]{
    def map[X](f: R => X):Result[X, A]
    def flatMap[X, AA >: A](f: R => Result[X, AA]):Result[X, AA]
  }
}

sealed trait Result[+R, +A] { result =>
  import Result._

  def map[RR >: R, B](f:A => B):Result[RR, B] =
    flatMap[RR, B](value => Success(f(value)))

  def flatMap[RR >: R, B](f:A => Result[RR, B]):Result[RR, B] = this match {
    case Success(a)        => f(a)
    case Failure(response) => Failure(response)
    case Error(response)   => Error(response)
  }

  def orElse[RR >: R, B >: A](next: => Result[RR, B]):Result[RR, B] = this match {
    case Failure(_)      => next
    case Error(response) => Error(response)
    case Success(value)  => Success(value)
  }

  def and[B, E, RF](other: => Result[JoiningResponseFunction[E,RF], B])
    (implicit ev: R <:< JoiningResponseFunction[E,RF])
    : Result[JoiningResponseFunction[E,RF], (A,B)] = {
    (this, other) match {
      case (Success(a), Success(b)) => Success((a, b))
      case (Failure(fa), Failure(fb)) => Failure(fa.join(fb))
      case (Success(a), Failure(fb)) => Failure(fb)
      case (Failure(fa), Success(b)) => Failure(fa)
      case (Error(fa), Error(fb)) => Error(fa.join(fb))
      case (Success(a), Error(fb)) => Error(fb)
      case (Error(fa), Success(b)) => Error(fa)
      case (Failure(fa), Error(fb)) => Error(fa.join(fb))
      case (Error(fa), Failure(fb)) => Error(fa.join(fb))
    }
  }

  def fail:FailResult[R, A] = new FailResult[R, A]{
    def flatMap[X, AA >: A](f: R => Result[X, AA]): Result[X, AA] = result match {
      case Success(a)     => Success(a)
      case Failure(r)     => f(r)
      case Error(r)       => f(r)
    }
    def map[X](f: R => X) = result match {
      case Success(a)     => Success(a)
      case Failure(r)     => Failure(f(r))
      case Error(r)       => Error(f(r))
    }
  }
}
