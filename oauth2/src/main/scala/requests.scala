package unfiltered.oauth2

import unfiltered.request.{HttpRequest => Req}

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.1
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3.1.1
 */
sealed trait AuthorizationRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.3
 */
sealed trait AccessRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.1
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-2.3
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3.1.2
 */
case class AuthorizationCodeRequest[T](
  req: Req[T],
  clientId: String,
  redirectURI: String,
  scope: Option[String],
  state: Option[String]
) extends AuthorizationRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.1
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-2.3
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3.1.2
 */
case class ImplicitAuthorizationRequest[T](
  req: Req[T],
  clientId: String,
  redirectURI: String,
  scope: Option[String],
  state: Option[String]
) extends AuthorizationRequest

case class IndeterminateAuthorizationRequest[T](
  req: Req[T],
  responseType: String,
  clientId: String,
  redirectURI: String,
  scope: Option[String],
  state: Option[String]
) extends AuthorizationRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.3
 */
case class AccessTokenRequest(
  code: String,
  redirectURI: String,
  clientId: String,
  clientSecret:String
) extends AccessRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.4.2
 */
case class ClientCredentialsRequest(
  clientId: String,
  secret: String,
  scope: Option[String]
) extends AccessRequest

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-6
 */
case class RefreshTokenRequest(
  refreshToken: String,
  clientId: String,
  clientSecret: String,
  scope: Option[String]
) extends AccessRequest
