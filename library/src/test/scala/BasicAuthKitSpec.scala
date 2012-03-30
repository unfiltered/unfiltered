package unfiltered.request

import org.specs._

object BasicAuthKitSpecJetty
extends unfiltered.spec.jetty.Planned
with BasicAuthKitSpec

object BasicAuthKitSpecNetty
extends unfiltered.spec.netty.Planned
with BasicAuthKitSpec

trait BasicAuthKitSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def valid(u: String, p: String) = (u,p) match { case ("test", "secret") => true case _ => false }

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = unfiltered.kit.Auth.basic(valid)({
    case _ => ResponseString("we're in")
  })

  "Basic Auth kit" should {
    shareVariables()
    "authenticate a valid user" in {
      val resp = http(host / "secret" as_!("test", "secret") as_str)
      resp must_=="we're in"
    }
    "not authenticate an invalid user and return a www-authenticate header" in {
      val hdrs = Http.when(_ == 401)((host / "secret" as_!("joe", "shmo")) >:> { h => h })
      hdrs must haveKey("WWW-Authenticate")
      val authenticate = hdrs("WWW-Authenticate")
      authenticate.headOption must beSome("""Basic realm="secret"""")
    }
  }
}
