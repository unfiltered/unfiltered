package unfiltered.request

import org.specs2.mutable._

object BasicAuthKitSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with BasicAuthKitSpecSync

object BasicAuthKitSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with BasicAuthKitSpecSync

object BasicAuthKitSpecNettyAsync
extends Specification
with unfiltered.specs2.netty.PlannedAsync
with BasicAuthKitSpecAsync

trait BasicAuthKitSpecSync extends BasicAuthKitSpec {
  import unfiltered.response._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = unfiltered.kit.Auth.basic(valid)({
    case _ => ResponseString("we're in")
  })
}

trait BasicAuthKitSpecAsync extends BasicAuthKitSpec {
  import unfiltered.response._

  def intent[A,B]: unfiltered.Async.Intent[A,B] = unfiltered.Async.Intent.fromSync(unfiltered.kit.Auth.basic(valid)({
    case _ => ResponseString("we're in")
  }))
}

trait BasicAuthKitSpec extends Specification with unfiltered.specs2.Hosted {

  def valid(u: String, p: String) = (u,p) match { case ("test", "secret") => true case _ => false }

  "Basic Auth kit" should {
    "authenticate a valid user" in {
      val resp = http(req(host / "secret").as_!("test", "secret")).as_string
      resp must_== "we're in"
    }
    "not authenticate an invalid user and return a www-authenticate header" in {
      val resp = httpx(req(host / "secret").as_!("joe", "shmo"))
      resp.code must_== 401
      val headers = resp.headers
      headers must haveKey("www-authenticate")
      val authenticate = headers("www-authenticate")
      authenticate.headOption must beSome("""Basic realm="secret"""")
    }
  }
}
