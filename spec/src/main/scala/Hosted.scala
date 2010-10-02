package unfiltered.spec

import org.specs._
import dispatch._

trait Hosted extends Specification {
  val port = unfiltered.Port.any
  val host = :/("localhost", port)
}
