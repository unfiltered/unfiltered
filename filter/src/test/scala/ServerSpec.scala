package unfiltered.server

import org.specs._

object ServerSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/", _)) => ResponseString("test") ~> Ok
  })}
  
  "A Server" should {
    "respond to requests" in {
      Http(host as_str) must_=="test"
    }
  }
}
