package unfiltered.netty

import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}

object NettyServerTest {
  def main(args: Array[String]) = {

    val p = unfiltered.netty.Planify( {
        case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
      })

    val ch = new UnfilteredChannelHandler {
      def intent = {
        case GET(UFPath("/", _)) => ResponseString("test") ~> Ok        
      }
    }
    // Tired.. Why can't I pass p here?
    new Server(8080, ch).start

  }
}
