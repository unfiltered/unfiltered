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

case class Strict[A,B,E](cf: A => Option[B], handle: A => ResponseFunction[E])
extends Interpreter[Option[A], Option[B], E] {
  def interpret(option: Option[A]): Either[ResponseFunction[E], Option[B]] = option.map { a =>
    cf(a).map(Some(_)).toRight(handle(a))
  }.getOrElse(Right(None))
}

object Predicate {
  def apply[A](f: A => Boolean) = Optional[A,A]( a => Some(a).filter(f))
}


/** Bridge class for finding an implicit As of a parameter type T */
object Of {
  def apply[T] = new Of[T]
}
class Of[T] {
  import unfiltered.directives.Directives._
  def named[E](name: String)(implicit to: Interpreter[Seq[String],T,E])
      : Directive[Any,E,T] =
    to named name
}
