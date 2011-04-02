package unfiltered.oauth2


trait OAuthResponse

sealed trait AuthorizationResponse extends OAuthResponse

sealed trait AccessResponse extends OAuthResponse

case class AuthorizationCodeResponse(
  code: String, state: Option[String]
) extends AuthorizationResponse

case class AccessTokenResponse(
  accessToken: String, tokenType: String, expiresIn: Option[Int],
  refreshToken: Option[String], scope: Option[String], state: Option[String]
) extends AccessResponse with AuthorizationResponse

case class ImplicitAccessTokenResponse(
  accessToken: String, tokenType: String, expiresInt: Option[Int],
  scope: Option[String], state: Option[String]
) extends AuthorizationResponse

case class AuthorizedPass(
  owner: String, scope: Option[String]
) extends OAuthResponse

case class HostResponse[T](
  rf: unfiltered.response.ResponseFunction[T]
) extends AuthorizationResponse with AccessResponse

case class ErrorResponse(
  error: String, desc: String, uri: Option[String], state: Option[String]
) extends AuthorizationResponse with AccessResponse

trait Formatting {
  def qstr(kvs: Seq[(String, String)]) =
    kvs map { _ match { case (k, v) => k + "=" + v } } mkString("&")
  def jsbody(kvs: Seq[(String, String)]) =
    kvs map { _ match { case (k, v) => "\"%s\":\"%s\"".format(k,v) } } mkString(
      "{",",","}"
    )
}
