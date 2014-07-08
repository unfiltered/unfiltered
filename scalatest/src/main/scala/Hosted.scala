package unfiltered.scalatest

import dispatch.classic._

trait Hosted {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)

  /** Silent, resource-managed http request executor */
  def http[T](handler: Handler[T]): T = {
    val h = Http
    try { h(handler) }
    finally { h.shutdown() }
  }

  /** Silent, resource-managed http request executor which accepts
      non-ok status */
  def xhttp[T](handler: Handler[T]): T  = {
    val h = Http
    try { h.x(handler) }
    finally { h.shutdown() }
  }
}
