package unfiltered.kit

import org.specs._
import unfiltered.spec

object SecureSpecJetty
extends spec.jetty.Planned
with SecureSpec

object SecureSpecNetty
extends spec.netty.Planned
with SecureSpec

trait SecureSpec extends spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] =
    unfiltered.kit.Secure.redir {
      case req@UFPath(Seg("a" :: "b" :: Nil)) =>
        ResponseString(req.isSecure.toString)
    }

  "Secure.redir should" should {
    "redirect a insecure request" in {
      http((host / "a" / "b") as_str) must_== "true"
    }
  }

}
