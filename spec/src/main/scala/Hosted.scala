package unfiltered.spec

import org.specs._
import dispatch._

trait Hosted extends Specification {
  val port = 9090
  val host = :/("localhost", port)
}
