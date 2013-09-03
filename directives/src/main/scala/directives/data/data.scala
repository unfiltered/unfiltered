package unfiltered.directives.data

import unfiltered.response.ResponseFunction
import unfiltered.directives._

trait Interpreter[A,B,+E] { self =>
  def interpret(a: A, name: String): Either[E, B]
  def ~> [C, EE >: E](implicit next: Interpreter[B,C,EE]): Interpreter[A,C,EE] =
    new Interpreter[A,C,EE] {
      def interpret(a: A, name: String): Either[EE,C] =
        self.interpret(a, name).right.flatMap {
          r => next.interpret(r, name)
        }
    }
  def named[EE >: E](name: String)(implicit to: Interpreter[Seq[String],A,EE]) =
    new Directive[Any,EE,B]( { req =>
      val seq = Option(req.parameterValues(name)).getOrElse(Nil)
      to.interpret(seq, name).right.flatMap { r =>
        self.interpret(r, name)
      }.fold(
        r => Result.Failure(r),
        r => Result.Success(r)
      )
    } )
}
object Interpreter {
  def identity[A] = new Interpreter[A, A, Nothing] {
    def interpret(seq: A, name: String) = Right(seq)
  }
  def apply[A,B](f: A => B) = new Interpreter[A, B, Nothing] {
    def interpret(a: A, name: String) = Right(f(a))
  }
}

case class Fallible[A,B](cf: A => Option[B])
extends Interpreter[Option[A], Option[B], Nothing] {
  def interpret(opt: Option[A], name: String) =
    Right(opt.flatMap(cf))
  def fail[E](handle: (String, A) => E) =
    new Strict(cf, handle)
}

class Strict[A,B,+E](cf: A => Option[B], handle: (String, A) => E)
extends Interpreter[Option[A], Option[B], E] {
  def interpret(option: Option[A], name: String): Either[E, Option[B]] =
    option.map { a =>
      cf(a).map(Some(_)).toRight(handle(name, a))
    }.getOrElse(Right(None))
}
object Conditional {
  def apply[A](f: A => Boolean) = Fallible[A,A]( a => Some(a).filter(f))
}

object Requiring {
  def apply[A] = new RequireBuilder[A]
}
class Requiring[A,+E](handle: String => E)
extends Interpreter[Option[A], A, E] {
  def interpret(option: Option[A], name: String): Either[E, A] =
    option.toRight(handle(name))
}
class RequireBuilder[A] {
  def fail[E](handle: String => E) =
    new Requiring[A,E](handle)
}
