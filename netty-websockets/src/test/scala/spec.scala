package unfiltered.netty.websockets

import org.specs2.mutable.Specification
import unfiltered.request.GET
import unfiltered.request.{Path => UFPath}
import scala.collection.mutable

class WebSocketPlanSpec extends Specification with unfiltered.specs2.netty.Served {
  def setup = _.plan(Planify {
    case GET(UFPath("/")) => {
      case Open(s) =>
        s.send("open")
      case Message(s, Text(m)) =>
        s.send(m)
    }
  })

  def wsuri = host.toString.replace("http", "ws")

  "A websocket server" should {
    "accept connections" in {
      val m = mutable.Map.empty[String, String]
      tubesocks.Sock.uri(wsuri) {
        case tubesocks.Open(s) =>
          s.send("open")
        case tubesocks.Message(t, _) =>
          m += ("rec" -> t)
      }
      m must havePair(("rec", "open")).eventually
    }

    "handle messages" in {
      val m = mutable.Map.empty[String, String]
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
