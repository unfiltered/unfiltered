package unfiltered.netty

object FutureServerSpec extends unfiltered.spec.netty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import scala.concurrent.Future
  import unfiltered.netty.async._


  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  def setup = _.handler(async.Planify(FuturePlanify {
    case req @ GET(UFPath("/ping")) => Future.successful(ResponseString("pong"))

    case req @ GET(UFPath("/future-ping")) =>
      Future { ResponseString(http(host / "ping" as_str)) }

    case GET(UFPath("/pass")) => Future.successful(Pass)
  }))

  "A Server" should {
    "pass upstream on Pass, respond in last handler" in {
      skip("How to pass asynchronously?")
      http(host / "pass" as_str) must_== "pass"
    }
    "respond with future results" in {
      http(host / "future-ping" as_str) must_== "pong"
    }
  }
}
