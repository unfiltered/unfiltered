package unfiltered.oauth2

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan

/** After your application has obtained an access token, your app can use it to access APIs by
 * including it in either an oauth_token query parameter or an Authorization: OAuth header.
 *
 * To call API using HTTP header.
 *
 *     GET /api/1/feeds.js HTTP/1.1
 *     Authorization: OAuth YOUR_ACCESS_TOKEN
 *     Host: www.example.com
 */
case class Protection(source: AuthSource) extends ProtectionLike {
  val schemes = Seq(BearerAuth, QParamBearerAuth, MacAuth)
}

/** Provides OAuth2 protection implementation. Extend this trait to customize query string `oauth_token`, etc. */
trait ProtectionLike extends Plan {
  import javax.servlet.http.HttpServletRequest

  val source: AuthSource
  val schemes: Seq[AuthScheme]
  def intent = ((schemes map {_.intent(this)}) :\ fallback) {_ orElse _}
  def fallback: Plan.Intent = {
    case request => errorResponse(Unauthorized, "", request)
  }

  def authenticate[T <: HttpServletRequest](token: AccessToken, request: HttpRequest[T]) =
    source.authenticateToken(token, request) match {
      case Left(msg) => errorResponse(Unauthorized, msg, request)
      case Right((user, scopes)) =>
        request.underlying.setAttribute(OAuth2.XAuthorizedIdentity, user.id)
        scopes.map(request.underlying.setAttribute(OAuth2.XAuthorizedScopes, _))
        Pass
    }

  def errorString(status: String, description: String) = """error="%s" error_description="%s" """.trim format(status, description)

  def errorResponse[T](status: Status, description: String,
      request: HttpRequest[T]): Responder[Any] = (status, description) match {
    case (Unauthorized, "") => Unauthorized ~> WWWAuthenticate("Bearer") ~> ResponseString("Bearer")
    case (Unauthorized, _)  =>
      Unauthorized ~> WWWAuthenticate("Bearer\n" + errorString("invalid_token", description)) ~>
      ResponseString(errorString("invalid_token", description))

    case (BadRequest, _)    => status ~> ResponseString(errorString("invalid_request", description))
    case (Forbidden, _)     => status ~> ResponseString(errorString("insufficient_scope", description))
    case _ => status ~> ResponseString(errorString(status.toString, description))
  }
}

/** Represents the authorization source that issued the access token. */
trait AuthSource {
  def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, (ResourceOwner, Option[String])]

  def realm: Option[String] = None
}

/** Represents the authentication scheme. */
trait AuthScheme {
  def intent(protection: ProtectionLike): Plan.Intent
}
trait AccessToken

case class BearerToken(value: String) extends AccessToken

/** Represents Bearer auth. */
trait BearerAuth extends AuthScheme {
  val defaultBearerHeader = """Bearer ([\w|:|\/|.|%|-]+)""".r
  def header = defaultBearerHeader

  object BearerHeader {
    val HeaderPattern = header

    def unapply(hvals: List[String]) = hvals match {
      case HeaderPattern(token) :: Nil => Some(token)
      case _ => None
    }
  }

  def intent(protection: ProtectionLike) = {
    case Authorization(BearerHeader(token)) & request => protection.authenticate(BearerToken(token), request)
  }
}

object BearerAuth extends BearerAuth {}

/** Represents Bearer auth. */
trait QParamBearerAuth extends AuthScheme {
  val defaultQueryParam = "oauth_token"
  def queryParam = defaultQueryParam

  object BearerParam {
    def unapply(params: Map[String, Seq[String]]) = params.getOrElse(queryParam, Nil).headOption
  }

  def intent(protection: ProtectionLike) = {
    case Params(BearerParam(token)) & request => protection.authenticate(BearerToken(token), request)
  }
}

object QParamBearerAuth extends QParamBearerAuth {}

/** Represents MAC auth. */
trait MacAuth extends AuthScheme {
  val defaultMacHeader = "MAC"
  def header = defaultMacHeader

  val TokenKey = "token"
  val Timestamp = "timestamp"
  val Nonce = "nonce"
  val BodyHash = "bodyhash"
  val Sig = "signature"

  /** Authorization: MAC header extractor */
  object MacHeader {
    val KeyVal = """(\w+)="([\w|:|\/|.|%|-]+)" """.trim.r
    val keys = Set.empty + TokenKey + Timestamp + Nonce + BodyHash + Sig
    val headerSpace = header + " "

    def unapply(hvals: List[String]) = hvals match {
      case x :: xs if x startsWith headerSpace =>
        Some(Map(hvals map { _.replace(headerSpace, "") } flatMap {
          case KeyVal(k, v) if(keys.contains(k)) => Seq((k -> Seq(v)))
          case _ => Nil
        }: _*))

      case _ => None
    }
  }

  def intent(protection: ProtectionLike) = {
    case Authorization(MacHeader(p)) & request =>
      try {
        val token = MacAuthToken(p(TokenKey)(0), p(Timestamp)(0), p(Nonce)(0), p(BodyHash)(0), p(Sig)(0))
        protection.authenticate(token, request)
      }
      catch {
        case e: Exception => protection.errorResponse(BadRequest, "invalid MAC header.", request)
      }
  }
}

object MacAuth extends MacAuth {}

case class MacAuthToken(value: String,
  timestamp: String,
  nonce: String,
  bodyhash: String,
  signature: String) extends AccessToken
