package unfiltered.oauth2

trait OAuthResponse

sealed trait AuthorizationResponse extends OAuthResponse

sealed trait AccessResponse extends OAuthResponse

case class AuthorizationCodeResponse(
  code: String,
  state: Option[String]
) extends AuthorizationResponse

case class AccessTokenResponse(
  accessToken: String,
  tokenType: String,
  expiresIn: Option[Int],
  refreshToken: Option[String],
  scope: Option[String],
  state: Option[String]
) extends AccessResponse with AuthorizationResponse

case class ImplicitAccessTokenResponse(
  accessToken: String,
  tokenType: String,
  expiresIn: Option[Int],
  scope: Option[String],
  state: Option[String]
) extends AuthorizationResponse

case class AuthorizedPass(
  owner: String,
  scope: Option[String]
) extends OAuthResponse

case class ContainerResponse[T](
  handler: unfiltered.response.ResponseFunction[T]
) extends AuthorizationResponse

case class ErrorResponse(
  error: String,
  desc: String,
  uri: Option[String],
  state: Option[String]
) extends AuthorizationResponse with AccessResponse


trait Formatting {
  import java.net.URLEncoder
  def qstr(kvs: Seq[(String, String)]) =
    kvs map { _ match { case (k, v) => URLEncoder.encode(k, "utf-8") + "=" + URLEncoder.encode(v, "utf-8") } } mkString("&")

  def Json(kvs: Seq[(String, String)]) =
    unfiltered.response.ResponseString(kvs map { _ match { case (k, v) => "\"%s\":\"%s\"".format(k,v) } } mkString(
      "{",",","}"
    )) ~> unfiltered.response.JsonContent
}
