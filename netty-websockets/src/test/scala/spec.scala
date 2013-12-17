package unfiltered.netty.websockets

import com.ning.http.client.providers.netty.NettyAsyncHttpProviderConfig
import unfiltered.request.{ GET, Path => UFPath }
import java.net.URI
import scala.collection.mutable

object WebSocketPlanSpec extends unfiltered.spec.netty.Served {
  def setup = _.handler(Planify {
    case GET(UFPath("/")) => {
      case Open(s) =>
        s.send("open")
      case Message(s, Text(m)) =>
        s.send(m)
    }
  })

  def wsuri = host.to_uri.toString.replace("http", "ws")

  "A websocket server" should {
    "accept connections" in {
      var m = mutable.Map.empty[String, String]
      tubesocks.Sock.uri(wsuri) {
        case tubesocks.Open(s) =>
          s.send("open")
        case tubesocks.Message(t, _) =>
          m += ("rec" -> t)
      }
      m must havePair(("rec", "open")).eventually
    }

    "handle messages" in {
      var m = mutable.Map.empty[String, String]
      tubesocks.Sock.uri(wsuri) {
        case tubesocks.Open(s) =>
          s.send("from client")
        case tubesocks.Message(t, _) =>
          m += ("rec" -> t)
      }
      m must havePair(("rec", "from client")).eventually
    }
  }
}
