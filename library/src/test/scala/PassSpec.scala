package unfiltered.response

import org.specs2.mutable._

class PassSpecJetty extends Specification with unfiltered.specs2.jetty.Planned with PassSpec

class PassSpecNetty extends Specification with unfiltered.specs2.netty.Planned with PassSpec

trait PassSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  def intent[A, B]: unfiltered.Cycle.Intent[A, B] =
    Pass.onPass(intent1, intent2)

  def intent1[A, B]: unfiltered.Cycle.Intent[A, B] = { case GET(UFPath(Seg("intent1" :: Nil))) =>
    ResponseString("intent1")
  }
  def intent2[A, B]: unfiltered.Cycle.Intent[A, B] = { case GET(_) =>
    ResponseString("intent2")
  }

  "Pass" should {
    "match in the first intent" in {
      val resp = http(host / "intent1").as_string
      resp must_== "intent1"
    }
    "match in the second intent" in {
      val resp = http(host / "whatever").as_string
      resp must_== "intent2"
    }
    "not match with POST" in {
      val resp = httpx(req(host) << Map("what" -> "oh"))
      resp.code must_== 404
    }
  }
}
