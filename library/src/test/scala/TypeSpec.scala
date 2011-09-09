package unfiltered.request

import org.specs._
import unfiltered.spec

object TypeSpecJetty extends spec.jetty.Planned with TypeSpec
object TypeSpecNetty extends spec.netty.Planned with TypeSpec

trait TypeSpec extends spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  val message = "élo"

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = unfiltered.kit.GZip {
    case UFPath("/test") => PlainTextContent ~> ResponseString(message)
    case UFPath("/latin") =>
      unfiltered.response.Charset(
        java.nio.charset.Charset.forName("iso-8859-1")) ~>
      PlainTextContent ~> ResponseString(message)
  }

  "ContentType should" should {
    "Correctly encode response in utf8 by default" in {
      val (resp, enc) = http((host / "test").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Type") })
      })
      resp must_== message
      enc must_== Set("text/plain; charset=utf-8")
    }
    "Correctly encode response in iso-8859-1 if requested" in {
      val (resp, enc) = http((host / "latin").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Type") })
      })
      resp must_== message
      enc must_== Set("text/plain; charset=iso-8859-1")
    }
  }
}
