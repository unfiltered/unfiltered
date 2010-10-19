package unfiltered.netty

import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}

object NettyServerTest {
  def main(args: Array[String]) = {

    val p = unfiltered.netty.roundtrip.Planify( {
        case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
      })

    new Server(8080, p).start
  }
}
