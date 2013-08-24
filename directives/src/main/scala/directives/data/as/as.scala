package unfiltered.directives.data.as

import scala.util.control.Exception.allCatch
import unfiltered.directives._
import unfiltered.directives.data._

import unfiltered.response.ResponseFunction

object Int extends Optional[String,Int](s => allCatch.opt { s.toInt })

object Long extends Optional[String,Long](s => allCatch.opt { s.toLong })

object BigInt extends Optional[String,BigInt](s =>
  allCatch.opt { new java.math.BigInteger(s) }
)

object Float extends Optional[String,Float](s => allCatch.opt { s.toFloat })

object Double extends Optional[String,Double](s => allCatch.opt { s.toDouble })

object BigDecimal extends Optional[String,BigDecimal](s =>
  allCatch.opt { new java.math.BigDecimal(s) }
)

object String extends Interpreter[Seq[String], Option[String], Nothing] {
  def interpret(seq: Seq[String], name: String) = Right(seq.headOption)

  val trimmed = Interpreter[Option[String],Option[String]]( opt => opt.map { _.trim } )
  val nonEmpty = Predicate[String]( _.nonEmpty )
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
