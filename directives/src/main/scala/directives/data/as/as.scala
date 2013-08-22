package unfiltered.directives.data.as

import scala.util.control.Exception.allCatch
import unfiltered.directives._
import unfiltered.directives.data._

import unfiltered.response.ResponseFunction

object Int extends Optional[String,Int](s => allCatch.opt { s.toInt })

object Float extends Optional[String,Float](s => allCatch.opt { s.toFloat })

object String extends Interpreter[Seq[String], Option[String], Nothing] {
  def interpret(seq: Seq[String], name: String) = Right(seq.headOption)
}
object Option {
  def apply[T] = new OptionalImplicit[T]
}
object Require {
  def apply[T] = new RequireImplicit[T]
}

/** Bridge class for finding an implicit As of a parameter type T */
class OptionalImplicit[T] {
  import unfiltered.directives.Directives._
  def named[E](name: String)
    (implicit to: Interpreter[Seq[String],Option[T],E])
    : Directive[Any,E,Option[T]] =
    to named name
}

class RequireImplicit[T] {
  import unfiltered.directives.Directives._
  def named[E](name: String)
    (implicit to: Interpreter[Seq[String],Option[T],E],
      req: Interpreter[Option[T],T,E])
      : Directive[Any,E,T] =
    (to ~> req) named name
}
