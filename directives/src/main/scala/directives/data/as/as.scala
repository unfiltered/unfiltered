package unfiltered.directives.data.as

import scala.util.control.Exception.allCatch
import unfiltered.directives.data._

object Int extends Optional[String,Int](s => allCatch.opt { s.toInt })

object Float extends Optional[String,Float](s => allCatch.opt { s.toFloat })

object String extends Interpreter[Seq[String], Option[String], Any] {
  def interpret(seq: Seq[String]) = Right(seq.headOption)
}
