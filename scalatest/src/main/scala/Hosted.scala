package unfiltered.scalatest

import dispatch._

trait Hosted {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)
  def http[T](handler: => Handler[T]): T = {
    val h = new Http
    try { h(handler) }
    finally { h.shutdown() }
  }
}
