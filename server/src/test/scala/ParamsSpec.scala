package unfiltered.request

import org.specs._

object ParamsSpec extends Specification {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  
  class TestPlan extends unfiltered.Planify({
    case GET(UFPath("/qp", Params(params, _))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }
    case POST(UFPath("/pp", Params(params, _))) => params("foo") match {
      case Seq(foo) => ResponseString("foo is %s" format foo)
      case _ =>  ResponseString("what's foo?")
    }
  })
  
  val server = unfiltered.server.Http(8081).filter(new TestPlan)
  val host = :/("localhost", 8081)
  
  doBeforeSpec { server.daemonize() }
  
  "Params" should {
    "extract query params" in {
      Http(host / "qp" <<? Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
    "extract post params" in {
      Http(host / "pp" << Map("foo" -> "bar") as_str) must_=="foo is bar"
    }
  }
  
  doAfterSpec { server.stop() }
}