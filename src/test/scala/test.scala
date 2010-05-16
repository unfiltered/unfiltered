package unfiltered.test

import unfiltered.request._
import unfiltered.response._

object AId extends scala.util.matching.Regex(
  """/a/(\d+)"""
)

class Test extends unfiltered.Handler ({
  case GET(Path(req, AId(id))) => println(id); Pass
  case GET(Segs(req, "b", id)) => println(id); Pass
})
