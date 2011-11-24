package unfiltered.response

/** Tells the binding implentation to treat the request as non-matching */
object Pass extends ResponseFunction[Any] {
  def apply[T](res: HttpResponse[T]) = res

  def orElse[A, B, A1 <: A, B1 >: B](
    intent: PartialFunction[A,B],
    onPass: PartialFunction[A1, B1]
  ): PartialFunction[A1, B1] =
    new OrElseAttempt(asAttempt(intent), asAttempt(onPass))

  def orElse[A, B, A1 <: A, B1 >: B](
    intent: PartialFunction[A,B],
    onPass: Function1[A1, B1]
  ): Function[A1, B1] =
    new OrElseAttempt(asAttempt(intent), new FunctionAttempt(onPass))

  def fold[A, B, A1 >: A, A2 >: A, C](
    intent: PartialFunction[A,B],
    onPass: A1 => C,
    andThen: (A2, B) => C
  ) = (a: A) =>
    asAttempt(intent).attempt(a).map { b =>
      andThen(a, b)
    }.getOrElse {
      onPass(a)
    }

  private def asAttempt[A,B](pf: Function[A,B]): Attempt[A,B] =
    pf match {
      case pa: Attempt[_,_] => pa
      case pf: PartialFunction[_,_] => new PartialAttempt(pf)
      case f: Function[_,_] => new FunctionAttempt(pf)
    }
  private trait Attempt[-A,+B] extends PartialFunction[A,B]{
    def attempt(x: A): Option[B]
  }
  private trait PassingAttempt[-A,+B] extends Attempt[A,B]{
    def attemptWithPass(x: A): Option[B]
    def attempt(x: A) = attemptWithPass(x).filter { _ != Pass }
  }
  private class PartialAttempt[-A,+B](underlying: PartialFunction[A,B])
  extends PassingAttempt[A,B] {
    val lifted = underlying.lift
    def isDefinedAt(x: A) = underlying.isDefinedAt(x)
    def apply(x: A) = underlying.apply(x)
    def attemptWithPass(x: A) = lifted(x)
  }
  private class FunctionAttempt[-A,+B](underlying: A => B)
  extends PassingAttempt[A,B] {
    def isDefinedAt(x: A) = true
    def apply(x: A) = underlying(x)
    def attemptWithPass(x: A) = Some(underlying(x))
  }
  private class OrElseAttempt[A,B,A1 <: A, B1 >: B](
    left: Attempt[A,B],
    right: Attempt[A1,B1]
  ) extends Attempt[A1,B1] {
    def isDefinedAt(x: A1): Boolean = {
      left.isDefinedAt(x) || right.isDefinedAt(x)
    }
    def apply(x: A1): B1 = { 
      left.attempt(x) orElse {
        right.attempt(x)
      } getOrElse {
        throw new MatchError(x)
      }
    }
    def attempt(x: A1): Option[B1] = { 
      left.attempt(x).orElse {
        right.attempt(x)
      }
    }
  }
}
