package unfiltered.server

import org.specs._

object PlanSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.filter._
  
  import dispatch._
  
  def setup = { 
    _.filter(Planify {
      case GET(UFPath("/filter", _)) => Pass
      case _ => println("nonmatching first"); Pass
    }).filter(Planify {
      case GET(UFPath("/filter", _)) => ResponseString("test") ~> Ok
    }).context("/filter2") {
      _.filter(Planify {
        case GET(UFPath("/test2", _)) => ResponseString("test2") ~> Ok
      })
    }.context("/filter3") {
      _.filter(new Plan { def intent = {
        case GET(UFPath(Seg("aplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan A")
      }})
      .filter(new Plan { def intent = {
        case GET(UFPath(Seg("bplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan B")
      }})
    }.context("/filter4") {
      _.filter(new unfiltered.filter.Planify({
        case GET(UFPath(Seg("aplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan A")
      }))
      .filter(new unfiltered.filter.Planify({
        case GET(UFPath(Seg("bplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan B")
      }))
      .filter(new unfiltered.filter.Planify({
        case GET(UFPath(Seg("cplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan C")
      }))
    }.context("/filter5") {
      _.filter(unfiltered.filter.Planify {
        case GET(UFPath(Seg("aplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan A")
      })
      .filter(unfiltered.filter.Planify {
        case GET(UFPath(Seg("bplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan B")
      })
      .filter(unfiltered.filter.Planify {
        case GET(UFPath(Seg("cplan" :: Nil), r)) =>
          Ok ~> ContentType("text/html") ~> ResponseString("Plan C")
      })
    }
  }
  
  "A Plan" should {
    "filter on the same context path" in {
      Http(host / "filter" as_str)  must_=="test"
    }
    "filter on a second context path with overlapping name" in {
      Http(host / "filter2" / "test2" as_str)  must_=="test2"
    }
    "filter multiple Plans on the same context path" in {
      Http(host / "filter3" / "aplan" as_str) must_=="Plan A"
      Http(host / "filter3" / "bplan" as_str) must_=="Plan B"
    }
    "filter multiple Planify classes on the same context path" in {
      Http(host / "filter4" / "aplan" as_str) must_=="Plan A"
      Http(host / "filter4" / "bplan" as_str) must_=="Plan B"
      Http(host / "filter4" / "cplan" as_str) must_=="Plan C"
    }
    "filter multiple Planify function calls on the same context path" in {
      Http(host / "filter5" / "aplan" as_str) must_=="Plan A"
      Http(host / "filter5" / "bplan" as_str) must_=="Plan B"
      Http(host / "filter5" / "cplan" as_str) must_=="Plan C"
    }
  }
}
