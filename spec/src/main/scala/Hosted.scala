package unfiltered.spec

import org.specs._
import dispatch._

trait Hosted extends Specification {
  val port = unfiltered.util.Port.any
  val host = :/("localhost", port)
  def http[T](handler: Handler[T]): T = {
    val h = new Http
    try { h(handler) }
    finally { h.shutdown() }
  }
}
