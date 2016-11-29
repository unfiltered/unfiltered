package unfiltered.request

import org.specs2.mutable._

object BasicAuthKitSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with BasicAuthKitSpec

object BasicAuthKitSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with BasicAuthKitSpec

import scala.collection.JavaConverters._

trait BasicAuthKitSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  def valid(u: String, p: String) = (u,p) match { case ("test", "secret") => true case _ => false }

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = unfiltered.kit.Auth.basic(valid)({
    case _ => ResponseString("we're in")
  })

  "Basic Auth kit" should {
    "authenticate a valid user" in {
      val resp = http(req(host / "secret").as_!("test", "secret")).as_string
      resp must_== "we're in"
    }
    "not authenticate an invalid user and return a www-authenticate header" in {
      val resp = httpx(req(host / "secret").as_!("joe", "shmo"))
      resp.code must_== 401
      val headers = resp.headersAsScala
      headers must haveKey("www-authenticate")
      val authenticate = headers("www-authenticate")
      authenticate.headOption must beSome("""Basic realm="secret"""")
    }
  }
}
