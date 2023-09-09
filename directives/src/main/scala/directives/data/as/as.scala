package unfiltered.directives.data.as

import scala.util.control.Exception.allCatch
import unfiltered.directives._
import unfiltered.directives.data._

object Int extends Fallible[String,Int](s => allCatch.opt { s.toInt })

object Long extends Fallible[String,Long](s => allCatch.opt { s.toLong })

object BigInt extends Fallible[String,BigInt](s =>
  allCatch.opt { math.BigInt(s) }
)

object Float extends Fallible[String,Float](s => allCatch.opt { s.toFloat })

object Double extends Fallible[String,Double](s => allCatch.opt { s.toDouble })

object BigDecimal extends Fallible[String,BigDecimal](s =>
  allCatch.opt { new java.math.BigDecimal(s) }
)

object String extends Interpreter[Seq[String], Option[String], Nothing] {
  def interpret(seq: Seq[String], name: String): Either[Nothing, Option[String]] = Right(seq.headOption)

  val trimmed = Interpreter[Option[String],Option[String]]( opt => opt.map { _.trim } )
  val nonEmpty = Conditional[String]( _.nonEmpty )
}

object Option {
  def apply[T] = new FallibleImplicit[T]
}

object Required {
  def apply[T] = new RequiredImplicit[T]
}

/** Bridge class for finding an implicit As of a parameter type T */
class FallibleImplicit[T] {
  import unfiltered.directives.Directives._
  def named[E](name: String)
    (implicit to: Interpreter[Seq[String],Option[T],E])
    : Directive[Any,E,Option[T]] =
    to named name
}

class RequiredImplicit[T] {
  import unfiltered.directives.Directives._
  def named[E](name: String)
    (implicit to: Interpreter[Seq[String],Option[T],E],
      req: Interpreter[Option[T],T,E])
      : Directive[Any,E,T] =
    (to ~> req) named name
}
