package unfiltered.mac

import org.specs2.mutable._
import org.specs2.matcher.ThrownMessages

object MacSpec extends Specification with ThrownMessages with unfiltered.specs2.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  System.setProperty("file.encoding", "UTF-8")

  val MacKey = "489dks293j39"

  def setup = {
    _.plan(unfiltered.filter.Planify {
      case UFPath("/echo") & MacAuthorization(id, nonce, bodyhash, ext, mac) =>
        ResponseString("id %s nonce %s bodyhash %s ext %s mac %s" format(id, nonce, bodyhash, ext, mac))
      case UFPath("/echo") => Mac.challenge
      case MacAuthorization(id, nonce, bodyhash, ext, mac) & r =>
        Mac.sign(r, nonce, ext, bodyhash, MacKey, "hmac-sha-1").fold({ err =>
          Mac.challenge(Some("invalid Authorization")) ~> ResponseString(err)
        }, { sig =>
           if(mac != sig) Mac.challenge(Some("%s did not eq %s" format(mac, sig)))
           else ResponseString(sig)
        })
    })
  }

  "Mac" should {
    "respond with a challege when client omits authorization" in {
      val resp = httpx(req(host / "echo"))
      resp.code must_== 401
      val headers = resp.headers
      headers must havePair(("www-authenticate", List("MAC")))
     }
    "respond with a challenge when required authorization params are missing" in {
      val resp = httpx(host / "echo")
      resp.code must_== 401
      val headers = resp.headers
      headers must havePair(("www-authenticate", List("MAC")))

    }
    "respond with a challege with a malformed nonce" in {
      val resp = httpx(req(host / "echo") <:< Map(
        "Authorization" -> """MAC id="%s",nonce="%s",mac="%s" """.format("test_id", "test:test", "asdfasdf")))
      resp.code must_== 401
      val headers = resp.headers
      headers must havePair(("www-authenticate", List("MAC")))
     }
    "respond with a body when authorization is valid" in {
       val body = httpx(req(host / "echo") <:<  Map(
         "Authorization" -> """MAC id="%s",nonce="%s",mac="%s" """.format("test_id", "123:test", "asdfasdf"))).as_string
       body must_== "id test_id nonce 123:test bodyhash None ext None mac asdfasdf"
    }
    "respond with a body when authorization is valid" in {
       val body = httpx(req(host / "echo") <:<  Map(
         "Authorization" -> """MAC id="%s",nonce="%s",mac="%s",bodyhash="%s",ext="%s" """.format(
           "test_id", "123:test", "asdfasdf","asdfasf", java.net.URLEncoder.encode("a,b,c", "utf8")))).as_string
       body must_== "id test_id nonce 123:test bodyhash Some(asdfasf) ext Some(a%2Cb%2Cc) mac asdfasdf"
    }
    "respond ok with with a valid mac signed request" in {
       val (key, nonce, method,  uri, hostname, hport, bodyhash, ext) = (
         MacKey, "264095:dj83hs9s", "GET", "/resource/1?b=1&a=2", host.host(), port, "", "")

       val normalizedRequest = Mac.requestString(nonce, "GET", uri, hostname, hport, bodyhash, ext)
       Mac.macHash("hmac-sha-1", key)(normalizedRequest).fold({
         fail(_)
       }, { mac =>
         val auth = Map("Authorization" -> """MAC id="%s",nonce="%s",mac="%s"""".format(
            "h480djs93hd8", nonce, mac
          ))
         val body = http(req(host / "resource" / "1" <<? Map("b"->"1", "a"->"2")) <:< auth).as_string
         body must_== mac
       })
    }
  }
}
