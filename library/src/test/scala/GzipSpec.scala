package unfiltered.request

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.util.zip.GZIPInputStream

import okio.ByteString
import org.specs2.mutable._
import okhttp3.{MediaType, RequestBody}

class GzipSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with GZipSpec

class GzipSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with GZipSpec

trait GZipSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import java.io.ByteArrayOutputStream
  import java.util.zip.{GZIPOutputStream => GZOS}


  val message = "message"

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] =
    unfiltered.kit.GZip {
      unfiltered.kit.GZip.Requests {
        case UFPath(Seg("empty" :: Nil)) => Ok ~> ResponseString("")
        case req @ UFPath(Seg("echo" :: Nil)) => {
          ResponseString(Body.string(req))
        }
        case req@UFPath(Seg("test" :: Nil)) => ResponseString(message)
      }
    }

  def gzipDecode(response: Response) = {
    val body = response.body
    body.map(bs =>
      scala.io.Source.fromInputStream(new GZIPInputStream(new ByteArrayInputStream(bs.toByteArray))).mkString
    ).getOrElse("")
  }

  "GZip response kit should" should {
    "gzip-encode a response when accepts header is present" in {
      val resp = http(req(host / "test") <:< Map("Accept-Encoding" -> "gzip"))
      resp.firstHeader("Content-Encoding") must_== Some("gzip")
      gzipDecode(resp) must_== message
    }
    "gzip-encode an empty response when accepts header is present" in {
      val resp = http(req(host / "empty") <:< Map("Accept-Encoding" -> "gzip"))
      resp.firstHeader("Content-Encoding") must_== Some("gzip")
      gzipDecode(resp) must_== ""
    }
    "serve unencoded response when accepts header is not present" in {
      val resp = http(req(host / "test"))
      resp.firstHeader("Content-Encoding") must_== None
      resp.as_string must_== message
    }
  }
  "GZip request kit should" should {
    val expected = "légère"
    val bos = {
      val bos = new ByteArrayOutputStream
      val zipped = new GZOS(bos)
      zipped.write(expected.getBytes("iso-8859-1"))
      zipped.close()
      val arr = bos.toByteArray
      ByteString.of(arr, 0, arr.length)
    }
    val ubos = {
      val ubos = new ByteArrayOutputStream
      val zipped = new GZOS(ubos)
      zipped.write(expected.getBytes("utf-8"))
      zipped.close()
      val arr = ubos.toByteArray
      ByteString.of(arr, 0, arr.length)
    }

    "echo an unencoded request" in {
      val isobody = RequestBody.create(MediaType.parse("text/plain; charset=iso-8859-1"), expected.getBytes(StandardCharsets.ISO_8859_1))
      val msg = http(req(host / "echo").POST(isobody)).as_string
      msg must_== expected
    }
    "echo an zipped request" in {
      val msg = http((req(host / "echo") <:< Map("Content-Encoding" -> "gzip")).POST(bos, MediaType.parse("text/plain"))).as_string
      msg must_== expected
    }
    "pass an non-matching request" in {
      val resp = httpx(host / "unknown")
      resp.code must_== 404
    }
    "pass an non-matching zipped request" in {
      val resp = httpx(req(host / "unknown").<:<(Map("Content-Encoding" -> "gzip")).POST(bos, MediaType.parse("text/plain")))
      resp.code must_== 404
    }
    "echo a utf-8 request" in {
      val msg = http(req(host / "echo").POST(expected, MediaType.parse("text/plain; charset=utf-8"))).as_string
      msg must_== expected
    }
    "echo a utf-8 zipped request" in {
      val msg = http(req(host / "echo").<:<(Map("Content-Encoding" -> "gzip")).POST(ubos, MediaType.parse("text/plain; charset=utf-8"))).as_string
      msg must_== expected
    }
  }
}
