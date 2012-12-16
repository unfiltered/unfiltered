package unfiltered.mac

import org.specs._

object MacSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import dispatch._

  System.setProperty("file.encoding", "UTF-8")

  val MacKey = "489dks293j39"

  def setup = {
    _.filter(unfiltered.filter.Planify {
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
       Http.when(_ == 401)(host / "echo" >:> { h =>
         h must havePair(("WWW-Authenticate", Set("MAC")))
       })
     }
    "respond with a challenge when required authorization params are missing" in {
      Http.when(_ == 401)(host / "echo" >:> { h =>
        h must havePair(("WWW-Authenticate", Set("MAC")))
      })
    }
    "respond with a challege with a malformed nonce" in {
       Http.when(_ == 401)(host / "echo" <:< Map(
         "Authorization" -> """MAC id="%s",nonce="%s",mac="%s" """.format("test_id", "test:test", "asdfasdf")) >:> { h =>
         h must havePair(("WWW-Authenticate", Set("MAC")))
       })
     }
    "respond with a body when authorization is valid" in {
       val body = Http(host / "echo" <:<  Map(
         "Authorization" -> """MAC id="%s",nonce="%s",mac="%s" """.format("test_id", "123:test", "asdfasdf")) as_str)
       body must_== "id test_id nonce 123:test bodyhash None ext None mac asdfasdf" 
    }
    "respond with a body when authorization is valid" in {
       val body = Http(host / "echo" <:<  Map(
         "Authorization" -> """MAC id="%s",nonce="%s",mac="%s",bodyhash="%s",ext="%s" """.format(
           "test_id", "123:test", "asdfasdf","asdfasf", java.net.URLEncoder.encode("a,b,c", "utf8"))) as_str)
       body must_== "id test_id nonce 123:test bodyhash Some(asdfasf) ext Some(a%2Cb%2Cc) mac asdfasdf"
    }
    "respond ok with with a valid mac signed request" in {
       val (key, nonce, method,  uri, hostname, hport, bodyhash, ext) = (
         MacKey, "264095:dj83hs9s", "GET", "/resource/1?b=1&a=2",  host.to_uri.getHost, port, "", "")

       val normalizedRequest = Mac.requestString(nonce, "GET", uri, hostname, hport, bodyhash, ext)
       Mac.macHash("hmac-sha-1", key)(normalizedRequest).fold({
         fail(_)
       }, { mac =>
         val auth = Map("Authorization" -> """MAC id="%s",nonce="%s",mac="%s"""".format(
            "h480djs93hd8", nonce, mac
          ))
         val body = Http(
           host / "resource" / "1" <:< auth <<? Map("b"->"1", "a"->"2") as_str
         )
         body must_== mac
       })
    }
  }
}
