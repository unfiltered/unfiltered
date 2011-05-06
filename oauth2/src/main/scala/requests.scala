package unfiltered.oauth2

import unfiltered.request.{HttpRequest => Req}

sealed trait AuthorizationRequest

sealed trait AccessRequest

case class AuthorizationCodeRequest[T](
  req: Req[T],
  clientId: String,
  redirectURI: String,
  scope: Option[String],
  state: Option[String]
) extends AuthorizationRequest

case class ImplicitAuthorizationRequest[T](
  req: Req[T],
  clientId: String,
  redirectURI: String,
  scope: Option[String],
  state: Option[String]
) extends AuthorizationRequest

case class AccessTokenRequest(
  code: String,
  redirectURI: String,
  clientId: String,
  clientSecret:String
) extends AccessRequest

case class ClientCredentialsRequest(
  clientId: String,
  secret: String,
  scope: Option[String]
) extends AccessRequest

case class RefreshTokenRequest(
  refreshToken: String,
  clientId: String,
  clientSecret: String,
  scope: Option[String]
) extends AccessRequest
