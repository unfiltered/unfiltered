package unfiltered.server

import org.specs2.mutable._

object PlanSpec extends Specification with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.filter._
  import request.ContextPath

  def setup = {
    _.plan(Planify {
      case UFPath("/filter") => Pass
    }).plan(Planify {
      case UFPath("/filter") => ResponseString("test")
    }).context("/filter2") {
      _.plan(Planify {
        case ContextPath(_, "/test2") => ResponseString("test2")
      })
    }.context("/filter3") {
      _.plan(new Plan { def intent = {
        case ContextPath(_, Seg("aplan" :: Nil)) => ResponseString("Plan A")
      }})
      .plan(new Plan { def intent = {
        case ContextPath(_, Seg("bplan" :: Nil)) => ResponseString("Plan B")
      }})
    }.context("/filter4") {
      _.plan(new unfiltered.filter.Planify({
        case ContextPath(_, Seg("aplan" :: Nil)) =>  ResponseString("Plan A")
      }))
      .plan(new unfiltered.filter.Planify({
        case ContextPath(_, Seg("bplan" :: Nil)) => ResponseString("Plan B")
      }))
      .plan(new unfiltered.filter.Planify({
        case ContextPath(_, Seg("cplan" :: Nil)) => ResponseString("Plan C")
      }))
    }.context("/filter5") {
      _.plan(unfiltered.filter.Planify {
        case ContextPath(_, Seg("aplan" :: Nil)) => ResponseString("Plan A")
      })
      .plan(unfiltered.filter.Planify {
        case ContextPath(_, Seg("bplan" :: Nil)) => ResponseString("Plan B")
      })
      .plan(unfiltered.filter.Planify {
        case ContextPath(_, Seg("cplan" :: Nil)) => ResponseString("Plan C")
      })
    }.context("/query") {
      _.plan(unfiltered.filter.Planify {
        case ContextPath(_, "/qplan") & QueryString(qs) => ResponseString(qs)
      })
    }
  }

  "A Plan" should {
    "filter on the same context path" in {
      http(host / "filter").as_string must_== "test"
    }
    "filter on a second context path with overlapping name" in {
      http(host / "filter2" / "test2").as_string must_== "test2"
    }
    "filter multiple Plans on the same context path" in {
      http(host / "filter3" / "aplan").as_string must_== "Plan A"
      http(host / "filter3" / "bplan").as_string must_== "Plan B"
    }
    "filter multiple Planify classes on the same context path" in {
      http(host / "filter4" / "aplan").as_string must_== "Plan A"
      http(host / "filter4" / "bplan").as_string must_== "Plan B"
      http(host / "filter4" / "cplan").as_string must_== "Plan C"
    }
    "filter multiple Planify function calls on the same context path" in {
      http(host / "filter5" / "aplan").as_string must_== "Plan A"
      http(host / "filter5" / "bplan").as_string must_== "Plan B"
      http(host / "filter5" / "cplan").as_string must_== "Plan C"
    }
    "filter must extract query string" in {
      http(host / "query" / "qplan" <<? new Map.Map2("foo", "bar", "baz", "boom")).as_string must_== "foo=bar&baz=boom"
    }
  }
}
