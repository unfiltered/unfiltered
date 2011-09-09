package unfiltered.request

import org.specs._

object DecodesSpecJetty
extends unfiltered.spec.jetty.Planned
with DecodesSpec

object DecodesSpecNetty
extends unfiltered.spec.netty.Planned
with DecodesSpec

trait DecodesSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Chunked(_)) => ResponseString("chunked")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Identity(_)) => ResponseString("identity")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.GZip(_)) => ResponseString("gzip")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Compress(_)) => ResponseString("compress")
    case GET(UFPath(Seg(ext :: Nil)) & Decodes.Deflate(_)) => ResponseString("deflate")
  }

  "Decodes should" should {
    "match a chunked accepts encoding request as chunked" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "chunked")  as_str)
      resp must_=="chunked"
    }
    "match a mixed chunked accepts encoding request as chunked" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "chunked;q=1.0, gzip; q=0.5")  as_str)
      resp must_=="chunked"
    }
    "match an identity accepts encoding request as identity" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "identity")  as_str)
      resp must_=="identity"
    }
    "match a mixed identity accepts encoding request as identity" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "gzip;q=1.0, identity; q=0.5")  as_str)
      resp must_=="identity"
    }
    "match a gzip accepts encoding request as gzip" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "gzip")  as_str)
      resp must_=="gzip"
    }
    "match a mixed gzip accepts encoding request as gzip" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "Compress;q=1.0, gzip; q=0.5")  as_str)
      resp must_=="gzip"
    }
    "match a compress accepts encoding request as compress" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "compress")  as_str)
      resp must_=="compress"
    }
    "match a mixed compress accepts encoding request as compress" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "deflate;q=1.0, compress; q=0.5")  as_str)
      resp must_=="compress"
    }
    "match a deflate accepts encoding request as deflate" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "deflate")  as_str)
      resp must_=="deflate"
    }
    "match a mixed deflate accepts encoding request as deflate" in {
      val resp = http(host / "test" <:< Map("Accept-Encoding" -> "deflate;q=1.0, invalid; q=0.5")  as_str)
      resp must_=="deflate"
    }
  }
}
