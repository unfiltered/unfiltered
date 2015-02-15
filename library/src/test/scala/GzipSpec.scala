package unfiltered.request

import org.specs2.mutable._
import unfiltered.spec

object GzipSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with GZipSpec

object GzipSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with GZipSpec

trait GZipSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import java.io.{OutputStreamWriter,ByteArrayOutputStream}
  import java.util.zip.{GZIPOutputStream => GZOS}


  val message = "message"

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = 
    unfiltered.kit.GZip {
      unfiltered.kit.GZip.Requests {
        case UFPath(Seg("empty" :: Nil)) => Ok
        case req @ UFPath(Seg("echo" :: Nil)) => 
          new ResponseString(Body.string(req))
        case UFPath(Seg("test" :: Nil)) => ResponseString(message)
      }
    }

  "GZip response kit should" should {
    "gzip-encode a response when accepts header is present" in {
      val (resp, enc) = http((host / "test").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Encoding") })
      })
      resp must_== message
      enc must_== Set("gzip")
    }
    "gzip-encode an empty response when accepts header is present" in {
      val (resp, enc) = http((host / "empty").gzip  >+ { req =>
        (req as_str, req >:> { _("Content-Encoding") })
      })
      resp must_== ""
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
  "GZip request kit should" should {
    val expected = "légère"
    val bos = {
      val bos = new ByteArrayOutputStream
      val zipped = new GZOS(bos)
      zipped.write(expected.getBytes("iso-8859-1"))
      zipped.close()
      bos
    }
    val ubos = {
      val ubos = new ByteArrayOutputStream
      val zipped = new GZOS(ubos)
      zipped.write(expected.getBytes("utf-8"))
      zipped.close()
      ubos
    }

    "echo an unencoded request" in {
      val msg = http((host / "echo") << expected as_str)
      msg must_== expected
    }
    "echo an zipped request" in {
      val msg = http((host / "echo")
        <:< Map("Content-Encoding" -> "gzip")
        << ubos.toByteArray as_str)
      msg must_== expected
    }
    "pass an non-matching request" in {
      val status = xhttp(((host / "unknown")
        << expected >|) ((status, _, _, _) => status))
      status must_== 404
    }
    "pass an non-matching zipped request" in {
      val status = xhttp(((host / "unknown")
        <:< Map("Content-Encoding" -> "gzip")
        << bos.toByteArray >|) ((status, _, _, _) => status))
      status must_== 404
    }
    "echo a utf-8 request" in {
      val msg = http((host / "echo")
        <:< Map(
          "Content-Type" -> "text/plain; charset=utf-8")
        << expected as_str)
      msg must_== expected
    }
    "echo a utf-8 zipped request" in {
      val msg = http((host / "echo")
        <:< Map(
          "Content-Encoding" -> "gzip",
          "Content-Type" -> "text/plain; charset=utf-8")
        << ubos.toByteArray as_str)
      msg must_== expected
    }
  }
}
