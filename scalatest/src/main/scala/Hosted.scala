package unfiltered.scalatest

import dispatch._

trait Hosted {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)
}
