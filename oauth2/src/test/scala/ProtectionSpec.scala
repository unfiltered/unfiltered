package unfiltered.oauth2

import org.specs._

object ProtectionSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import dispatch._

  class User(val id: String) extends ResourceOwner {
    override val password = None
  }

  object TestClient extends Client {
    def redirectUri = "http://example.com"
    def secret = "client_secret"
    def id = "test_client"
  }

  object User {
    import javax.servlet.http.{HttpServletRequest}

    def unapply[T <: HttpServletRequest](request: HttpRequest[T]): Option[User] =
      request.underlying.getAttribute(unfiltered.oauth2.OAuth2.XAuthorizedIdentity) match {
        case id: String => Some(new User(id))
        case _ => None
      }
  }

  val GoodBearerToken = """!#$%&'()*+=./:<=>?@[]^_`{|}~\good_token7"""
  val GoodMacToken = "good_token"

  def setup = { server =>
    // source is responsible for authenticating a request given an extracted access token
    val source = new AuthSource {
      def authenticateToken[T](
          access_token: AccessToken, request: HttpRequest[T]): Either[String, (ResourceOwner, String, Seq[String])] =
        access_token match {
          case BearerToken(GoodBearerToken) =>
            Right((new User("test_user"), TestClient.id, Nil))
          case MacAuthToken(GoodMacToken, _, _, _, _) =>
            Right((new User("test_user"), TestClient.id, Nil))
          case _ =>
            Left("bad token")
        }

      override def realm: Option[String] = Some("Mock Source")
    }
    server.filter(Protection(source))
    .filter(unfiltered.filter.Planify {
      case User(user) => ResponseString(user.id)
    })
  }

  "oauth 2" should {
    "authenticate a valid access token via query parameter" in {
      val oauth_token = Map("access_token" -> GoodBearerToken)
      try {
        Http.x(host / "user" <<? oauth_token as_str) must_== "test_user"
      } catch {
        case dispatch.StatusCode(code, _) =>
          fail("got unexpected status code %s" format code)
      }
    }

    "authenticate a valid access token via Bearer header" in {
      val bearer_header = Map("Authorization" -> "Bearer %s".format(GoodBearerToken))
      try {
        Http.x(host / "user" <:< bearer_header as_str) must_== "test_user"
      } catch {
        case dispatch.StatusCode(code, _) =>
          fail("got unexpected status code %s" format code)
      }
    }

    // see http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-08#section-2.4.1
    "fail on a bad Bearer header" in {
      val bearer_header = Map("Authorization" -> "Bearer bad_token")
      Http.when(_ == 401)(host / "user" <:< bearer_header as_str) must_== """error="%s" error_description="%s" """.trim.format("invalid_token", "bad token")
      val head = Http.when(_ == 401)(host / "user" <:< bearer_header >:> { h => h })
      head.get("WWW-Authenticate") must_!= (None)
      val wwwAuth = head("WWW-Authenticate")
      val expected = "Bearer error=\"%s\",error_description=\"%s\"".trim.format("invalid_token", "bad token")
      wwwAuth.head must_== expected
    }

    // see http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-08#section-2.4.1
    "fail without returning an error code and description on missing authentication information" in {
      val head = Http.when(_ == 401)(host / "user" >:> { h => h })
      head.get("WWW-Authenticate") must_!= (None)
      val wwwAuth = head("WWW-Authenticate")
      wwwAuth.head must_== "Bearer"
    }
    // see http://tools.ietf.org/html/draft-ietf-oauth-v2-http-mac-00#section-4.1
    "fail on a bad MAC header" in {
      val macHeader = Map("Authorization" -> """MAC id="bogus",nonce="123:y",bodyhash="bogus",mac="bogus"""")
      val head = Http.when(_ == 401)(host / "user" <:< macHeader >:> { h => h })
      head.get("WWW-Authenticate") must_!= (None)
      val wwwAuth = head("WWW-Authenticate")
      val expected = "MAC error=\"%s\"".trim.format("invalid token")
      wwwAuth.head must_== expected
    }

/*
    "authenticate a valid access token via MAC header" in {
      val mac_header = Map("Authorization" -> """MAC id="%s",nonce="123:x",bodyhash="x",mac="x" """.format(GoodMacToken).trim)
      Http(host / "user" <:< mac_header as_str) must_== "test_user"
    }*/
  }
}
