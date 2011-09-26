package unfiltered.oauth2

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan

/**
 * After your application has obtained an access token, your app can use it to access APIs by
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
    case request => println("did not match any scheme in %s" format schemes);errorResponse(Unauthorized, "", request)
  }

  def authenticate[T <: HttpServletRequest](token: AccessToken, request: HttpRequest[T]) =
    source.authenticateToken(token, request) match {
      case Left(msg) => errorResponse(Unauthorized, msg, request)
      case Right((user, scopes)) =>
        request.underlying.setAttribute(OAuth2.XAuthorizedIdentity, user.id)
        scopes.map(request.underlying.setAttribute(OAuth2.XAuthorizedScopes, _))
        Pass
    }

  def errorString(status: String, description: String) =
    """error="%s" error_description="%s" """.trim format(status, description)

  def errorResponse[T](status: Status, description: String,
      request: HttpRequest[T]): ResponseFunction[Any] = (status, description) match {
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
  def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, (ResourceOwner, Seq[String])]

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
  val defaultBearerHeader = """Bearer ([\w\d!#$%&'\(\)\*+\-\.\/:<=>?@\[\]^_`{|}~\\,;]+)""".r
  def header = defaultBearerHeader

  object BearerHeader {
    val HeaderPattern = header

    def unapply(hval: String) = hval match {
      case HeaderPattern(token) => Some(token)
      case _ => None
    }
  }

  def intent(protection: ProtectionLike) = {
    case Authorization(BearerHeader(token)) & request =>
      protection.authenticate(BearerToken(token), request)
  }
}

object BearerAuth extends BearerAuth {}

/** Represents Bearer auth. */
trait QParamBearerAuth extends AuthScheme {
  val defaultQueryParam = "bearer_token"
  def queryParam = defaultQueryParam

  object BearerParam {
    def unapply(params: Map[String, Seq[String]]) = params(queryParam).headOption
  }

  def intent(protection: ProtectionLike) = {
    case Params(BearerParam(token)) & request =>
      protection.authenticate(BearerToken(token), request)
  }
}

object QParamBearerAuth extends QParamBearerAuth {}

/** Represents MAC auth. */
trait MacAuth extends AuthScheme {
  import unfiltered.mac.{Mac, MacAuthorization}

  def algorithm: String

  def tokenSecret(key: String): Option[String]

  def intent(protection: ProtectionLike) = {
    case MacAuthorization(id, nonce, bodyhash, ext, mac) & req =>
      try {
         tokenSecret(id) match {
           case Some(key) =>
              Mac.sign(req, nonce, ext, bodyhash, key, algorithm).fold({ err =>
                println("err signing %s" format err)
                protection.errorResponse(BadRequest, err, req)
              }, { sig =>
                println("sig %s" format sig)
                println("mac %s" format mac)
                if(sig == mac) protection.authenticate(MacAuthToken(id, key, nonce, bodyhash, ext), req)
                else protection.errorResponse(BadRequest, "invalid MAC signature", req)
             })
           case _ =>
             println("could not find token for id %s" format id)
             protection.errorResponse(BadRequest, "invalid token", req)
         }
      }
      catch {
        case e: Exception => protection.errorResponse(BadRequest, "invalid MAC header.", req)
      }
  }
}

object MacAuth extends MacAuth {
  def algorithm = "hmac-sha-1"
  def tokenSecret(key: String) = None
}

case class MacAuthToken(id: String,
  secret: String,
  nonce: String,
  bodyhash: Option[String],
  ext: Option[String]
  ) extends AccessToken
