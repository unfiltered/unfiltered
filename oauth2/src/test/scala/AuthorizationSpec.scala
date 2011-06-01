package unfiltered.oauth2

import org.specs._

object AuthorizationSpec
  extends Specification
  with unfiltered.spec.jetty.Served {

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  import scala.util.parsing.json.JSON
  import java.net.{URI, URLDecoder}

  val client = MockClient(
    "mockclient", "mockserver",
    "http://localhost:%s/echo" format port
  )

  val owner = MockResourceOwner("doug")

  def setup = { server =>
    val authProvider = new MockAuthServerProvider(client, owner)
    server.context("/oauth") {
      _.filter(OAuthorization(authProvider.auth))
    }
    .context("/echo") {
      _.filter(unfiltered.filter.Planify {
        case UFPath(p) => ResponseString("echo: %s" format p)
      })
    }
  }

  val authorize = host / "oauth" / "authorize"
  val token = host / "oauth" / "token"

  // turning off redirects for validation
  def http = new Http {
    override def make_client = {
      val c = super.make_client
      c.setRedirectHandler(new org.apache.http.client.RedirectHandler() {
         import org.apache.http.protocol.HttpContext
         import org.apache.http.{HttpResponse=>HcResponse}
         def getLocationURI(res: HcResponse, ctx: HttpContext) = null
         def isRedirectRequested(res: HcResponse, ctx: HttpContext) = false
      })
      c
    }
  }

  def json[T](body: String)(f: Map[String, Any] => T) =
    JSON.parseFull(body) match {
      case Some(obj) => f(
        obj.asInstanceOf[Map[String, Any]]
      )
      case _ => fail("could not parse body")
    }

  val ErrorQueryString = """error=(\S+)&error_description=(\S+)$""".r

  //
  // implicit flow spec
  //
  "OAuth2 requests for response_type 'token'" should {
    // http://tools.ietf.org/html/draft-ietf-oauth-v2-15#section-2.1
    "require a `response_type`, encoding errors as query string params" in {
       val head = http.x(authorize <<? Map(
          "client_id" -> client.id,
          "redirect_uri" -> client.redirectUri
       ) >:> { h => h })
      head must haveKey("Location")
      new URI(head("Location").head).getQuery match {
        case ErrorQueryString(err, desc) =>
           err must_==("invalid_request")
           URLDecoder.decode(desc,"utf-8") must_==("response_type is required")
        case uri => fail("invalid redirect %s" format uri)
      }
    }
    // http://tools.ietf.org/html/draft-ietf-oauth-v2-15#section-4.2.1
    "require a `client_id`, encoding errors as url fragment params" in {
       val head = http.x(authorize <<? Map(
         "response_type" -> "token",
         "redirect_uri" -> client.redirectUri
       ) >:> { h => h })
       head must haveKey("Location")
       new URI(head("Location").head).getFragment match {
         case ErrorQueryString(err, desc) =>
            err must_==("invalid_request")
            URLDecoder.decode(desc, "utf-8") must_==("client_id is required")
         case uri => fail("invalid redirect %s" format uri)
       }
    }
    "not redirect to an unknown client" in {
      val body = http.x(authorize <<? Map(
        "response_type" -> "token",
        "client_id" -> "bogus",
        "redirect_uri" -> "bogus"
      ) as_str)
      body must_==("invalid client")
    }
    "not redirect to an invalid redirect_uri" in {
      val body = http.x(authorize <<? Map(
        "response_type" -> "token",
        "client_id" -> client.id,
        "redirect_uri" -> "bogus"
      ) as_str)
      body must_==("missing or invalid redirect_uri")
    }
    // http://tools.ietf.org/html/draft-ietf-oauth-v2-15#section-4.2.2.1
    "require a `redirect_uri`, encoding errors by notifying the resource owner" in {
       val body = http.x(authorize <<? Map(
         "response_type" -> "token",
         "client_id" -> client.id
       ) as_str)
       body must_==("missing or invalid redirect_uri")
    }
    // http://tools.ietf.org/html/draft-ietf-oauth-v2-15#section-4.2.2
    "receive access token in the form of a url fragment" in {
       val head = http.x(authorize <<? Map(
         "response_type" -> "token",
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri,
         "state" -> "test_state"
       ) >:> { h => h })
       head must haveKey("Location")
       val uri = new URI(head("Location").head)
       val Frag = """access_token=(\S+)&token_type=(\S+)&expires_in=(\d+)&state=(\S+)""".r
       Frag.findFirstMatchIn(uri.getFragment) must beSome
    }
  }

  //
  // client credentials flow
  //
  "OAuth2 requests for grant type client_credentials" should {
    "require a grant_type" in {
      val body = http(token << Map(
         "client_id" -> client.id,
         "client_secret" -> client.redirectUri
      ) as_str)
      json(body) { map =>
        map must havePair("error", "invalid_request")
        map must havePair("error_description", "grant_type is required")
      }
    }
    "require a client_id" in {
       val (head, body) = http(token << Map(
          "grant_type" -> "client_credentials",
          "client_secret" -> client.secret
       ) >+ { r => (r >:> { h => h }, r as_str ) })
       json(body) { map =>
         map must havePair("error", "invalid_request")
         map must havePair("error_description", "client_id is required")
       }
    }
    "not redirect to an unknown client" in {
      val body = http.x(authorize <<? Map(
        "response_type" -> "token",
        "client_id" -> "bogus",
        "redirect_uri" -> "bogus"
      ) as_str)
      body must_==("invalid client")
    }
    "not redirect to an invalid redirect_uri" in {
      val body = http.x(authorize <<? Map(
        "response_type" -> "token",
        "client_id" -> client.id,
         "redirect_uri" -> "bogus"
      ) as_str)
      body must_==("missing or invalid redirect_uri")
    }
    "require a client_secret" in {
       val (head, body) = http(token << Map(
         "grant_type" -> "client_credentials",
         "client_id" -> client.id
       ) >+ { r => (r >:> { h => r }, r as_str ) })
       json(body) { map =>
         map must havePair("error", "invalid_request")
         map must havePair("error_description", "client_secret is required")
       }
    }
  }

  //
  // authorization code flow spec
  //
  "OAuth2 requests for response_type 'code'" should {
    "require a response_type" in {
       val head = http.x(authorize <<? Map(
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri
       )  >:> { h => h })
       head must haveKey("Location")
       val uri = new URI(head("Location").head)
       uri.getQuery match {
         case ErrorQueryString(err, desc) =>
            err must_==("invalid_request")
            URLDecoder.decode(desc, "utf-8") must_=="response_type is required"
         case _ => fail("invalid redirect %s" format uri)
       }
    }
    "require a client_id" in {
       val head = http.x(authorize <<? Map(
         "response_type" -> "code",
         "redirect_uri" -> client.redirectUri
       ) >:> { h => h })
       head must haveKey("Location")
       val uri = new URI(head("Location").head)
       uri.getQuery match {
        case ErrorQueryString(err, desc) =>
          err must_==("invalid_request")
          URLDecoder.decode(desc, "utf-8") must_=="client_id is required"
        case _ => fail("invalid redirect %s" format uri)
      }
    }

    "require a redirect_uri" in {
       val body = http.x(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> client.id
       ) as_str)
       body must_==("missing or invalid redirect_uri")
    }

    "not accept invalid credentials in a basic auth header" in {
       val head = http.x(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri
       )  >:> { h => h })
       val Code = """code=(\S+)""".r
       new java.net.URI(head("Location").head).getQuery match {
          case Code(code) =>
             val invalid = http.x(token << Map(
               "grant_type" -> "authorization_code",
               "client_id" -> "bogus",
               "redirect_uri" -> client.redirectUri
             ) as_!(client.id, client.secret) as_str)
            json(invalid) { map =>
              map must haveKey("error")
            }
          case _ => fail("failed to retrieve authorization code")
       }
    }

    "provide a authorization code flow" in {
       val head = http.x(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri,
         "state" -> "test_state"
       )  >:> { h => h })
       head must haveKey("Location")
       val uri = new java.net.URI(head("Location").head)
       val Code = """code=(\S+)""".r
       val State = """state=(\S+)""".r
       Code.findFirstMatchIn(uri.getQuery) must beSome
       State.findFirstMatchIn(uri.getQuery) must beSome
       uri.getQuery match {
         case Code(code) =>
           var ares =
             http.x(token << Map(
               "grant_type" -> "authorization_code",
               "client_id" -> client.id,
               "redirect_uri" -> client.redirectUri,
               "code" -> code
             ) as_!(client.id, client.secret) as_str)
           json(ares) { map =>
             map must haveKey("access_token")
             map must haveKey("expires_in")
             map must haveKey("refresh_token")
             val rres = http.x(token << Map(
               "grant_type" -> "refresh_token",
               "client_id" -> client.id,
               "client_secret" -> client.secret,
               "refresh_token" -> map("refresh_token")
             ) as_str)
             json(rres) { map2  =>
               map2 must haveKey("access_token")
               map2 must haveKey("expires_in")
               map2 must haveKey("refresh_token")
             }
           }
         case _ => fail("!")
       }
    }
  }
}
