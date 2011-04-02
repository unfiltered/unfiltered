package unfiltered.oauth2

import org.specs._

object ProtectionSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import dispatch._

  class User(val id: String) extends UserLike
  object User {
    import javax.servlet.http.{HttpServletRequest}

    def unapply[T <: HttpServletRequest](request: HttpRequest[T]): Option[User] =
      request.underlying.getAttribute(unfiltered.oauth2.OAuth2.XAuthorizedIdentity) match {
        case user: User => Some(user)
        case _ => None
      }
  }

  def setup = { server =>
    val source = new AuthSource {
      def authenticateToken[T](access_token: AccessToken, request: HttpRequest[T]): Either[String, UserLike] =
        access_token match {
          case BearerToken("good_token")          => Right(new User("test_user"))
          case MacAuthToken("good_token", _, _, _, _) => Right(new User("test_user"))
          case _ => Left("bad token")
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
      val oauth_token = Map("oauth_token" -> "good_token")
      Http(host / "user" <<? oauth_token as_str) must_== "test_user"
    }

    "authenticate a valid access token via Bearer header" in {
      val bearer_header = Map("Authorization" -> "Bearer good_token")
      Http(host / "user" <:< bearer_header as_str) must_== "test_user"
    }

    "fail on a bad Bearer header" in {
      val bearer_header = Map("Authorization" -> "Bearer bad_token")
      Http.when(_ == 401)(host / "user" <:< bearer_header as_str) must_== """error="%s" error_description="%s" """.trim.format(
        "invalid_token", "bad token")
    }

    "authenticate a valid access token via MAC header" in {
      val mac_header = Map("Authorization" -> """MAC token="good_token",timestamp="x",nonce="x",bodyhash="x",signature="x" """.trim)
      Http(host / "user" <:< mac_header as_str) must_== "test_user"
    }
  }
}