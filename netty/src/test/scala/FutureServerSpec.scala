package unfiltered.netty

import org.specs2.mutable.Specification

import unfiltered.response.{ Pass, ResponseString }
import unfiltered.request.{ GET, Path => UFPath }
import scala.concurrent.Future

object FutureServerSpec extends Specification with org.specs2.matcher.ThrownMessages with unfiltered.specs2.netty.Served {

  implicit val executionContext = scala.concurrent.ExecutionContext.Implicits.global

  def setup = _.plan(future.Planify {
    case GET(UFPath("/ping")) =>
      Future.successful(ResponseString("pong"))

    case GET(UFPath("/future-ping")) =>
      Future { ResponseString(http(host / "ping").as_string) }

    case GET(UFPath("/pass")) =>
      Future.successful(Pass)
  })

  "A Server" should {
    "pass upstream on Pass, respond in last handler" in {
      skip("How to pass asynchronously?")
      http(host / "pass").as_string must_== "pass"
    }
    "respond with future results" in {
      http(host / "future-ping").as_string must_== "pong"
    }
  }
}
