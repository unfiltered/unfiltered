package unfiltered.request

import org.specs2.mutable._

class DecodesSpecJetty extends Specification with unfiltered.specs2.jetty.Planned with DecodesSpec

class DecodesSpecNetty extends Specification with unfiltered.specs2.netty.Planned with DecodesSpec

trait DecodesSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  def intent[A, B]: unfiltered.Cycle.Intent[A, B] = {
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Chunked(_)) => ResponseString("chunked")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Identity(_)) => ResponseString("identity")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.GZip(_)) => ResponseString("gzip")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Compress(_)) => ResponseString("compress")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Deflate(_)) => ResponseString("deflate")
  }

  "Decodes should" should {
    "match a chunked accepts encoding request as chunked" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "chunked")).as_string
      resp must_== "chunked"
    }
    "match a mixed chunked accepts encoding request as chunked" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "chunked;q=1.0, gzip; q=0.5")).as_string
      resp must_== "chunked"
    }
    "match an identity accepts encoding request as identity" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "identity")).as_string
      resp must_== "identity"
    }
    "match a mixed identity accepts encoding request as identity" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "gzip;q=1.0, identity; q=0.5")).as_string
      resp must_== "identity"
    }
    "match a gzip accepts encoding request as gzip" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "gzip")).as_string
      resp must_== "gzip"
    }
    "match a mixed gzip accepts encoding request as gzip" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "Compress;q=1.0, gzip; q=0.5")).as_string
      resp must_== "gzip"
    }
    "match a compress accepts encoding request as compress" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "compress")).as_string
      resp must_== "compress"
    }
    "match a mixed compress accepts encoding request as compress" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "deflate;q=1.0, compress; q=0.5")).as_string
      resp must_== "compress"
    }
    "match a deflate accepts encoding request as deflate" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "deflate")).as_string
      resp must_== "deflate"
    }
    "match a mixed deflate accepts encoding request as deflate" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "deflate;q=1.0, invalid; q=0.5")).as_string
      resp must_== "deflate"
    }
  }
}
