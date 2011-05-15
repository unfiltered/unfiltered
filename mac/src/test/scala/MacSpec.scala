package unfiltered.mac

import org.specs._

object MacSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import dispatch._
  def setup = {
    _.filter(unfiltered.filter.Planify {
      case MacAuthorization(id, nonce, bodyhash, ext, mac) =>
        ResponseString("id %s nonce %s bodyhash %s ext %s mac %s" format(id, nonce, bodyhash, ext, mac))
      case _ => Mac.Challenge
    })
  }

  "Mac" should {
    "respond with a challege when client omits authorization" in {
       Http.when(_ == 401)(host >:> { h =>
         h must havePair(("WWW-Authenticate", Set("MAC")))
       })
     }
    "respond with a challenge when required authorization params are missing" in {
      Http.when(_ == 401)(host >:> { h =>
        h must havePair(("WWW-Authenticate", Set("MAC")))
      })
    }
  }
}
