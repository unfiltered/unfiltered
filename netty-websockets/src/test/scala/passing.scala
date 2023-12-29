package unfiltered.netty.websockets

import org.specs2.mutable.Specification
import unfiltered.response.ResponseString
import unfiltered.request.{Path => UFPath}
import unfiltered.netty

/** tests for an unfiltered web sockets
 *  passing msgs along to a plan that
 *  can handle them
 */
class PassingSpec extends Specification with unfiltered.specs2.netty.Served {

  def setup =
    _.plan(planify { case UFPath("/a") =>
      ResponseString("http response a")
    }).plan(netty.websockets.Planify {
      case UFPath("/b") => {
        case Open(s) => s.send("socket opened b")
        case Message(s, Text(msg)) => s.send(msg)
      }
      case UFPath("/c") => Pass
    }.onPass(_.fireChannelRead(_)))
      .plan(netty.cycle.Planify {
        case UFPath("/b") => ResponseString("http response b")
        case UFPath("/c") => ResponseString("http response c")
      })

  "A websocket server" should {
    "not block standard http requests" in {
      http(host / "b").as_string must_== "http response b"
      http(host / "a").as_string must_== "http response a"
      http(host / "c").as_string must_== "http response c"
    }
  }
}
