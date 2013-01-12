package unfiltered
package specs2

import dispatch.classic._

trait Hosted {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)
  def logHttpRequests = false

  /** Silent, resource-managed http request executor */
  def http[T](handler: Handler[T]): T = {
    val h = if (logHttpRequests) new Http else new Http with NoLogging
    try { h(handler) }
    finally { h.shutdown() }
  }

  /** Silent, resource-managed http request executor which accepts
   *  non-ok status */
  def xhttp[T](handler: Handler[T]): T  = {
    val h = if(logHttpRequests) new Http else new Http with NoLogging
    try { h.x(handler) }
    finally { h.shutdown() }
  }

}
