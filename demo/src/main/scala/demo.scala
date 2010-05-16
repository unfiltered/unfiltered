package unfiltered.test

import unfiltered.request._
import unfiltered.response._

object AId extends scala.util.matching.Regex(
  """/a/(\d+)"""
)

class DemoHandler extends unfiltered.Handler ({
  case GET(Path(AId(id), req)) => println(id); Pass
  case GET(Path(Seg("b", id), req)) => println(id); Pass
})

object DemoServer {
  def main(args: Array[String]) {
    unfiltered.server.Http(8080)(new DemoHandler)
  }
}