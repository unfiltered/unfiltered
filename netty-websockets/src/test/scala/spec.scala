package unfiltered.netty.websockets

import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig
import unfiltered.request.{ Path => UFPath, _ }
import tubesocks.{
  Open => TOpen,
  Close => TClose,
  Message => TMessage,
  Error => TError,
  _
}
import java.net.URI
import java.util.concurrent.{ CountDownLatch, TimeUnit }
import scala.collection.mutable

object WebSocketPlanSpec extends unfiltered.spec.netty.Served {
  def setup = _.handler(Planify({
    case GET(UFPath("/")) => {
      case Open(s) =>
        s.send("open")
      case Message(s, Text(m)) =>
        s.send(m)
    }
  }))

  def wsuri = host.to_uri.toString.replace("http", "ws")

  "A websocket server" should {
    "accept connections" in {
      var m = mutable.Map.empty[String, String]
      Sock.uri(wsuri) {
        case TOpen(s) =>
          s.send("open")
        case TMessage(t, _) =>
          m += ("rec" -> t)
      }
      m must havePair(("rec", "open")).eventually
    }

    "handle messages" in {
      var m = mutable.Map.empty[String, String]
      Sock.uri(wsuri) {
        case TOpen(s) =>
          s.send("from client")
        case TMessage(t, _) =>
          m += ("rec" -> t)
      }
      m must havePair(("rec", "from client")).eventually
    }
  }
}
