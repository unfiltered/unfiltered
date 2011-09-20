package unfiltered.request

import org.specs._

object BasicAuthSpecJetty
extends unfiltered.spec.jetty.Planned
with BasicAuthSpec

object BasicAuthSpecNetty
extends unfiltered.spec.netty.Planned
with BasicAuthSpec

trait BasicAuthSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case GET(UFPath("/secret") & BasicAuth(name, pass)) => (name, pass) match {
      case ("test", "secret") => ResponseString("pass")
      case _ => ResponseString("fail")
    }
  }

  "Basic Auth" should {
    shareVariables()
    "authenticate a valid user" in {
      val resp = http(host / "secret" as_!("test", "secret") as_str)
      resp must_=="pass"
    }
    "not authenticate an invalid user" in {
      val resp = http(host / "secret" as_!("joe", "shmo") as_str)
      resp must_=="fail"
    }
  }
}
