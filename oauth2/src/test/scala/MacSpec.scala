package unfiltered.oauth2

import org.specs._

object MacSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  def setup = {
    _.filter(unfiltered.filter.Planify {
      case r => Mac.sign(r,"264095:7d8f3e4a", Some("a,b,c"), "key", "hmac-sha-1").fold({ResponseString(_)}, { ResponseString(_) })
    })
  }

  "Mac" should {
    "produce a properly encoded body hash" in {
      Mac.bodyhash("hello=world%21".getBytes(Mac.charset))(Mac.HmacSha1)
      .fold({ err => fail(err) }, { _ must_=="k9kbtCIy0CkI3/FEfpS/oIDjk6k=" })
    }
  }
}
