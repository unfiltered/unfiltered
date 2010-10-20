package unfiltered.netty

import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}

object NettyServerTest {
  def main(args: Array[String]) = {

    val p = unfiltered.netty.cycle.Planify( {
        case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
      })

    Server(8080, p).start
  }
}
