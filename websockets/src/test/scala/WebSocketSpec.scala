package unfiltered.netty.websockets

import org.specs._

object WebSocketsSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.netty
  import unfiltered.netty.{Http => NHttp}

  import dispatch._

  def setup =
    _.handler(planify {
      case UFPath("/a") => ResponseString("http response a")
    })
    .handler(netty.websockets.Planify({
      case UFPath("/b") => {
        case Open(s) => s.send("socket opened b")
        case Message(s, Text(msg)) => s.send(msg)
      }
    }).onPass(_.sendUpstream(_)))
    .handler(netty.cycle.Planify {
      case UFPath("/b") => ResponseString("http response b")
    })

  "A Websocket" should {
    "not block standard http requests" in {
      http(host / "b" as_str) must_==("http response b")
      http(host / "a" as_str) must_==("http response a")
    }
  }
}
