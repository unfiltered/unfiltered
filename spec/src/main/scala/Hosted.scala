package unfiltered.spec

import org.specs._
import dispatch.classic._

trait Hosted extends Specification {
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
  def xhttp[T](handler: dispatch.classic.Handler[T]): T  = {
    val h = if(logHttpRequests) new Http else new Http with NoLogging
    try { h.x(handler) }
    finally { h.shutdown() }
  }
}
