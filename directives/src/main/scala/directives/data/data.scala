package unfiltered.directives.data

import unfiltered.response.ResponseFunction
import unfiltered.directives._

trait Interpreter[A,B,-E] { self =>
  def interpret(a: A): Either[ResponseFunction[E], B]
  implicit def ~> [C, EE <: E](implicit next: Interpreter[B,C,EE]): Interpreter[A,C,EE] =
    new Interpreter[A,C,EE] {
      def interpret(a: A): Either[ResponseFunction[EE],C] = self.interpret(a).right.flatMap(next.interpret)
    }
  def named[EE <: E](name: String)(implicit to: Interpreter[Seq[String],A,EE]) =
    new Directive[Any,EE,B]( { req =>
      val seq = Option(req.parameterValues(name)).getOrElse(Nil)
      to.interpret(seq).right.flatMap(self.interpret).fold(
        r => Result.Failure(r),
        r => Result.Success(r)
      )
    } )
}
object Interpreter {
  def identity[A] = new Interpreter[A, A, Any] {
    def interpret(seq: A) = Right(seq)
  }
}

case class Optional[A,B](cf: A => Option[B])
extends Interpreter[Option[A], Option[B], Any] {
  def interpret(opt: Option[A]) = Right(opt.flatMap(cf))
  def fail[E](handle: A => ResponseFunction[E]) = new Strict(cf, handle)
}

class Strict[A,B,E](cf: A => Option[B], handle: A => ResponseFunction[E])
extends Interpreter[Option[A], Option[B], E] {
  def interpret(option: Option[A]): Either[ResponseFunction[E], Option[B]] =
    option.map { a =>
      cf(a).map(Some(_)).toRight(handle(a))
    }.getOrElse(Right(None))
}
object Predicate {
  def apply[A](f: A => Boolean) = Optional[A,A]( a => Some(a).filter(f))
}

object Required {
  def apply[A,E](handle: => ResponseFunction[E]) = new Required[A,E](handle)
}
class Required[A,E](handle: => ResponseFunction[E])
extends Interpreter[Option[A], A, E] {
  def interpret(option: Option[A]): Either[ResponseFunction[E], A] =
    option.toRight(handle)
}
