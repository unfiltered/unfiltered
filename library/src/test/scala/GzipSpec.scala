package unfiltered.request

import org.specs._
import unfiltered.spec

object GzipSpecJetty
extends spec.jetty.Planned
with GZipSpec

object GzipSpecNetty
extends spec.netty.Planned
with GZipSpec

trait GZipSpec extends spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  val message = "message"

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = unfiltered.kit.GZip {
    case _ => ResponseString(message)
  }

  "GZip should" should {
    "gzip-encode a response when accepts header is present" in {
      val (resp, enc) = http((host / "test").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Encoding") })
      })
      resp must_== message
      enc must_== Set("gzip")
    }
    "serve unencoded response when accepts header is not present" in {
      val (resp, enc) = http((host / "test")  >+ { req =>
        (req as_str, req >:> { _("Content-Encoding") })
      })
      resp must_== message
      enc must_== Set.empty
    }
  }
}
