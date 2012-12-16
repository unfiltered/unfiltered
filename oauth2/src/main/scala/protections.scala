package unfiltered.oauth2

import unfiltered.request._
import unfiltered.response._
import unfiltered.filter.Plan

/**
 * After your application has obtained an access token, your app can use it to access APIs by
 * including it in either an access_token query parameter or an Authorization: Beader header.
 *
 * To call API using HTTP header.
 *
 *     GET /api/1/feeds.js HTTP/1.1
 *     Host: www.example.com
 *     Authorization: Bearer vF9dft4qmT
 */
case class Protection(source: AuthSource) extends ProtectionLike {
  /** List or ResourceServer token schemes. By default,
   *  this includes BearerAuth which extracts Bearer tokens for a header,
   *  QParamBeaerAuth which extracts Bearer tokens request params and
   *  MacAuth which extracts tokens from using the `Mac` authentication encoding */
  val schemes = Seq(BearerAuth, QParamBearerAuth, MacAuth)
}

/** Provides OAuth2 protection implementation.
 *  Extend this trait to customize query string `oauth_token`, etc. */
trait ProtectionLike extends Plan {
  import javax.servlet.http.HttpServletRequest

  /** Provides a means of verifying a deserialized access token */
  val source: AuthSource

  /** Provides a list of schemes used for decoding access tokens in request */
  val schemes: Seq[AuthScheme]

  final def intent = ((schemes map { _.intent(this) }) :\ fallback) { _ orElse _ }
  
  /** If no authentication token is provided at all, demand the first
    * authentication scheme of all that are supported */
  def fallback: Plan.Intent = {
    case r =>
      schemes.head.errorResponse(Unauthorized, "", r)
  }

  /** Returns access token response to client */
  def authenticate[T <: HttpServletRequest](
    token: AccessToken, request: HttpRequest[T])(errorResp: (String => ResponseFunction[Any])) =
    source.authenticateToken(token, request) match {
      case Left(msg) => errorResp(msg)
      case Right((user, clientId, scopes)) =>
        request.underlying.setAttribute(OAuth2.XAuthorizedIdentity, user.id)
        request.underlying.setAttribute(OAuth2.XAuthorizedClientIdentity, clientId)
        request.underlying.setAttribute(OAuth2.XAuthorizedScopes, scopes)
        Pass
    }
}

/** Represents the authorization source that issued the access token. */
trait AuthSource {
  /** Given an deserialized access token and request, extract the resource owner, client id, and list of scopes
   *  associated with the request, if there is an error return it represented as a string message
   *  to return the the oauth client */
  def authenticateToken[T](
    token: AccessToken,
    request: HttpRequest[T]): Either[String, (ResourceOwner, String, Seq[String])]

  /**
   * Auth sources which 
   */
  def realm: Option[String] = None
}

/** Represents the scheme used for decoding access tokens from a given requests. */
trait AuthScheme {

  def intent(protection: ProtectionLike): Plan.Intent

  def errorString(status: String, description: String) =
    """error="%s" error_description="%s" """.trim format(status, description)

  /** The WWW-Authenticate challege returned to the clien tin a 401 response
   *  for invalid requests */
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

  /** Return a function representing an error response */
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

/** Represents Bearer auth encoded in a header.
 *  see also http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-14 */
trait BearerAuth extends AuthScheme {
  val challenge = "Bearer"
  val defaultBearerHeader = """Bearer ([\w\d!#$%&'\(\)\*+\-\.\/:<=>?@\[\]^_`{|}~\\,;]+)""".r
  
  /** bearer header format */
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

/** Represents Bearer auth encoded in query params.
 *  ses also http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-14 */
trait QParamBearerAuth extends AuthScheme {
  val challenge = "Bearer"
  val defaultQueryParam = "access_token"
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
  import unfiltered.mac.{ Mac, MacAuthorization }

  val challenge = "MAC"

  /** The algorigm used to sign the request */
  def algorithm: String

  /** Given a token value, returns the associated token secret */
  def tokenSecret(key: String): Option[String]

  def intent(protection: ProtectionLike) = {
    case MacAuthorization(id, nonce, bodyhash, ext, mac) & req =>
      try {
         tokenSecret(id) match {
           case Some(key) =>
             // compare a signed request with the signature provided
             Mac.sign(req, nonce, ext, bodyhash, key, algorithm).fold({ err =>
               errorResponse(Unauthorized, err, req)
             }, { sig =>
               if(sig == mac) protection.authenticate(MacAuthToken(id, key, nonce, bodyhash, ext), req) {
                 failedAuthenticationResponse
               }
               else errorResponse(Unauthorized, "invalid MAC signature", req)
             })
           case _ =>
             errorResponse(Unauthorized, "invalid token", req)
         }
      } catch {
        case _ => errorResponse(Unauthorized, "invalid MAC header.", req)
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
