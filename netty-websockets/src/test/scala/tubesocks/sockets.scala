package tubesocks

import com.ning.http.client.websocket.WebSocket

// we are using the netty client
// the grizly client implements streaming
// consider this...
trait Socket {
  def send(s: String): Unit
  def open: Boolean
  def close: Unit
}

class DefaultSocket(underlying: WebSocket) extends Socket {
  def send(s: String) =
    if (underlying.isOpen) underlying.sendTextMessage(s)
  def open = underlying.isOpen
  def close = if (underlying.isOpen) underlying.close
  override def toString() = "%s(%s)" format(
    getClass().getName, if(open) "open" else "closed")
}
