package unfiltered.request

import java.nio.charset.StandardCharsets

import org.specs2.mutable._

object TypeSpecJetty extends Specification with unfiltered.specs2.jetty.Planned with TypeSpec
object TypeSpecNetty extends Specification with unfiltered.specs2.netty.Planned with TypeSpec

trait TypeSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}


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
      val resp = http(host / "test")

      resp.as_string must_== message
      resp.firstHeader("Content-Type").map(_.toLowerCase).toList must containPattern("text/plain; ?charset=utf-8")
    }
    "Correctly encode response in iso-8859-1 if requested" in {
      val resp = http(host / "latin")

      resp.body.map(b => new String(b.toByteArray, StandardCharsets.ISO_8859_1)).getOrElse("") must_== message
      resp.firstHeader("Content-Type").map(_.toLowerCase).toList must containPattern("text/plain; ?charset=iso-8859-1")
    }
  }
}
