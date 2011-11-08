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
    case request =>
      println("did not match any scheme in %s" format schemes)
      // if no authentication token is provided at all, demand the first authentication scheme of all that are supported:
      schemes.head.errorResponse(Unauthorized, "", request)
  }

  def authenticate[T <: HttpServletRequest](token: AccessToken, request: HttpRequest[T])(errorResp: (String => ResponseFunction[Any])) =
    source.authenticateToken(token, request) match {
      //case Left(msg) => errorResponse(Unauthorized, msg, request)
      case Left(msg) => errorResp(msg)
      case Right((user, client, scopes)) =>
        request.underlying.setAttribute(OAuth2.XAuthorizedIdentity, user.id)
        request.underlying.setAttribute(OAuth2.XAuthorizedClientIdentity, client.id)
        request.underlying.setAttribute(OAuth2.XAuthorizedScopes, scopes)
        Pass
    }
}

/** Represents the authorization source that issued the access token. */
trait AuthSource {
  def authenticateToken[T](token: AccessToken, request: HttpRequest[T]): Either[String, (ResourceOwner, Client, Seq[String])]

  def realm: Option[String] = None
}

/** Represents the authentication scheme. */
trait AuthScheme {
  def intent(protection: ProtectionLike): Plan.Intent
  def errorString(status: String, description: String) =
    """error="%s" error_description="%s" """.trim format(status, description)
  val challenge: String
  /**
   * An error header, consisting of the challenge and possibly an error and error_description attribute
   * (this depends on the authentication scheme).
   */
  def errorHeader(error: Option[String] = None, description: Option[String] = None) = {
    val attrs = List("error" -> error, "error_description" -> description).collect { case (key, Some(value)) => key -> value }
    attrs.tail.foldLeft(
      attrs.headOption.foldLeft(challenge) { case (current, (key, value)) => """%s %s="%s"""".format(current, key, value) }
    ) { case (current, (key, value)) => current + ",\n%s=\"%s\"".format(key, value) }
  }

  /**
   * The response for failed authentication attempts. Intended to be overridden by authentication schemes that have
   * differing requirements.
   */
  val failedAuthenticationResponse: (String => ResponseFunction[Any]) = { msg =>
    Unauthorized ~> WWWAuthenticate(errorHeader(Some("invalid_token"), Some(msg))) ~>
      ResponseString(errorString("invalid_token", msg))
  }
  def errorResponse[T](status: Status, description: String,
      request: HttpRequest[T]): ResponseFunction[Any] = (status, description) match {
    case (Unauthorized, "") => Unauthorized ~> WWWAuthenticate(challenge) ~> ResponseString(challenge)
    case (Unauthorized, _)  => failedAuthenticationResponse(description)
    case (BadRequest, _)    => status ~> ResponseString(errorString("invalid_request", description))
    case (Forbidden, _)     => status ~> ResponseString(errorString("insufficient_scope", description))
    case _ => status ~> ResponseString(errorString(status.toString, description))
  }
}
trait AccessToken

case class BearerToken(value: String) extends AccessToken

/** Represents Bearer auth. */
trait BearerAuth extends AuthScheme {
  val challenge = "Bearer"
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
      protection.authenticate(BearerToken(token), request) { failedAuthenticationResponse }
  }
}

object BearerAuth extends BearerAuth {}

/** Represents Bearer auth. */
trait QParamBearerAuth extends AuthScheme {
  val challenge = "Bearer"
  val defaultQueryParam = "bearer_token"
  def queryParam = defaultQueryParam

  object BearerParam {
    def unapply(params: Map[String, Seq[String]]) = params(queryParam).headOption
  }

  def intent(protection: ProtectionLike) = {
    case Params(BearerParam(token)) & request =>
      protection.authenticate(BearerToken(token), request) { failedAuthenticationResponse }
  }
}

object QParamBearerAuth extends QParamBearerAuth {}

/** Represents MAC auth. */
trait MacAuth extends AuthScheme {
  import unfiltered.mac.{Mac, MacAuthorization}

  val challenge = "MAC"

  def algorithm: String

  def tokenSecret(key: String): Option[String]

  def intent(protection: ProtectionLike) = {
    case MacAuthorization(id, nonce, bodyhash, ext, mac) & req =>
      try {
         tokenSecret(id) match {
           case Some(key) =>
              Mac.sign(req, nonce, ext, bodyhash, key, algorithm).fold({ err =>
                println("err signing %s" format err)
                errorResponse(Unauthorized, err, req)
              }, { sig =>
                println("sig %s" format sig)
                println("mac %s" format mac)
                if(sig == mac) protection.authenticate(MacAuthToken(id, key, nonce, bodyhash, ext), req) {
                  failedAuthenticationResponse
                }
                else errorResponse(Unauthorized, "invalid MAC signature", req)
             })
           case _ =>
             println("could not find token for id %s" format id)
             errorResponse(Unauthorized, "invalid token", req)
         }
      }
      catch {
        case e: Exception => errorResponse(Unauthorized, "invalid MAC header.", req)
      }
  }

  /**
   * Whereas the Bearer token is supposed to return an error code in the error attribute and a human-readable
   * error description in the error_description attribute of the WWW-Authenticate header, for the MAC
   * authentication scheme, a human-readable error message may be supplied in the error attribute
   * (see http://tools.ietf.org/html/draft-ietf-oauth-v2-http-mac-00#section-4.1)
   */
  override val failedAuthenticationResponse: (String => ResponseFunction[Any]) = { msg =>
    Unauthorized ~> WWWAuthenticate(errorHeader(Some(msg))) ~> ResponseString("""error="%s"""".format(msg))
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
