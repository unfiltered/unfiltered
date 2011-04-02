package unfiltered.oauth2

import unfiltered.request.{HttpRequest => Req}

sealed trait AuthorizationRequest

sealed trait AccessRequest

case class AuthorizationCodeRequest[T](
  req: Req[T], responseType: String, clientId: String,
  redirectURI: String, scope: Option[String], state: Option[String]
) extends AuthorizationRequest

case class ImplicitAuthorizationRequest[T](
  req: Req[T], responseType: String, clientId: String,
  redirectURI: String, scope: Option[String], state: Option[String]
) extends AuthorizationRequest

/*
 case class OwnerCredsAuthorizationRequest(
  user: String, password: String
) extends AuthorizationRequest

case class ClientCredsAuthorizationRequest(
  clientId: String, secret: String, scope: Option[String]
) extends AuthorizationRequest
*/
case class AccessTokenRequest(
  grantType: String, code: String, redirectURI: String,
  clientId: String, clientSecret:String
) extends AccessRequest

case class ClientCredsAccessTokenRequest(
  grantType: String, clientId: String, secret: String,
  scope: Option[String]
) extends AccessRequest

case class RefreshTokenRequest(
  grantType: String, refreshToken: String, scope: Option[String]
) extends AccessRequest
