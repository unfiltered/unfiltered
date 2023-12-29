package tubesocks

import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.websocket.WebSocket
import com.ning.http.client.websocket.DefaultWebSocketListener
import com.ning.http.client.websocket.WebSocketUpgradeHandler
import java.net.URI

/** A builder of sorts for (Web)Sockets */
object Sock {

  /** A partial function signature for handing Socket events */
  type Handler = PartialFunction[Event, Any]

  object Listen {
    lazy val discard: Handler = { case e: Event =>
      ()
    }

    case class ReconnectingListen(times: Int, pausing: Int) {
      def apply(pf: Handler) = {
        def complete(e: Event) = (pf orElse discard)(e)
        // note: we would just use the TextListener below BUT
        // it's very convenient to make #onMessage(m) respondable
        new DefaultWebSocketListener {
          override def onMessage(m: String) = complete(Message(m, new DefaultSocket(this.webSocket)))
          override def onOpen(ws: WebSocket) = complete(Open(new DefaultSocket(ws)))
          override def onClose(ws: WebSocket) = complete(Close(new DefaultSocket(ws)))
          override def onError(t: Throwable) = complete(Error(t))
          override def onFragment(fragment: String, last: Boolean) =
            complete(if (last) EOF(fragment) else Fragment(fragment))
        }
      }
    }

    def reconnecting(times: Int, pausing: Int) = ReconnectingListen(times, pausing)

    def apply(pf: Handler) = {
      def complete(e: Event) = (pf orElse discard)(e)
      // note: we would just use the TextListener below BUT
      // it's very convenient to make #onMessage(m) respondable
      new DefaultWebSocketListener {
        override def onMessage(m: String) = complete(Message(m, new DefaultSocket(this.webSocket)))
        override def onOpen(ws: WebSocket) = complete(Open(new DefaultSocket(ws)))
        override def onClose(ws: WebSocket) = complete(Close(new DefaultSocket(ws)))
        override def onError(t: Throwable) = complete(Error(t))
        override def onFragment(fragment: String, last: Boolean) =
          complete(if (last) EOF(fragment) else Fragment(fragment))
      }
    }
  }

  def reconnecting(times: Int = -1, pausing: Int = 0)(uri: URI)(f: Handler): Socket =
    configure(identity)(times, pausing)(uri)(f)

  /** URI factory for returning a websocket
   *  @param str string uri
   *  @return a function that takes a Handler and returns a Socket */
  def uri(str: String) =
    apply(new URI(if (str.startsWith("ws")) str else s"ws://${str}")) _

  /** Default client-configured Socket
   *  @param uri websocket endpoint
   *  @param f Handler function */
  def apply(uri: URI)(f: Handler): Socket =
    configure(identity)()(uri)(f)

  /** Provides a means of customizing client configuration
   *  @param conf configuration building function
   *  @param uri websocket endpoint
   *  @param f Handler function */
  def configure(
    conf: AsyncHttpClientConfig.Builder => AsyncHttpClientConfig.Builder
  )(reconnectAttempts: Int = 0, pausing: Int = 0)(uri: URI)(f: Handler): Socket =
    new DefaultSocket(
      mkClient(conf(defaultConfig))
        .prepareGet(uri.toString)
        .execute(
          new WebSocketUpgradeHandler.Builder()
            .addWebSocketListener(Listen.reconnecting(reconnectAttempts, pausing)(f))
            .build()
        )
        .get()
    )

  private def defaultConfig =
    new AsyncHttpClientConfig.Builder().setUserAgent("Tubesocks/0.1")

  private def mkClient(config: AsyncHttpClientConfig.Builder) =
    new AsyncHttpClient(config.build())
}
