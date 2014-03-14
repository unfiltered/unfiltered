package unfiltered.request

import org.specs2.mutable._
import unfiltered.spec

object TypeSpecJetty extends unfiltered.specs2.jetty.Planned with TypeSpec
object TypeSpecNetty extends unfiltered.specs2.netty.Planned with TypeSpec

trait TypeSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch.classic._

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
      enc.map(_.toLowerCase) must containPattern("text/plain; ?charset=utf-8")
    }
    "Correctly encode response in iso-8859-1 if requested" in {
      val (resp, enc) = http((host / "latin").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Type") })
      })
      resp must_== message
      enc.map(_.toLowerCase) must containPattern("text/plain; ?charset=iso-8859-1")
    }
  }
}
