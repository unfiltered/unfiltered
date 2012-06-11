package unfiltered
package specs2

import dispatch._

/**
 * @author Erlend Hamnaberg<erlend@hamnaberg.net>
 */
trait Hosted {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)
  def http[T](handler: Handler[T]): T = {
    val h = new Http
    try { h(handler) }
    finally { h.shutdown() }
  }
}
