package unfiltered.netty.websockets

import unfiltered.request.{Path => UFPath, GET}
import unfiltered.response.{ResponseString, Ok}

import tubesocks.{
  Open => TOpen,
  Close => TClose,
  Message => TMessage,
  Error => TError,
  _
}

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
      import java.util.concurrent.{ CountDownLatch, TimeUnit }
      val m = scala.collection.mutable.Map.empty[String, String]
      val l = new CountDownLatch(1)
      Sock.uri(wsuri) {
        case TOpen(s) =>
          s.send("open")
        case TMessage(t, _) =>
          m += ("rec" -> t)
          l.countDown
      }
      l.await(4, TimeUnit.MILLISECONDS)
      m must havePair(("rec", "open"))
    }

    "handle messages" in {
      import java.util.concurrent.{ CountDownLatch, TimeUnit }
      var m = scala.collection.mutable.Map.empty[String, String]
      val l = new CountDownLatch(2)
      Sock.uri(wsuri) {
        case TOpen(s) =>
          s.send("from client")
        case TMessage(t, _) =>
          m += ("rec" -> t)
          l.countDown
      }
      l.await(4, TimeUnit.MILLISECONDS)
      m must havePair(("rec", "from client"))
    }
  }
}
