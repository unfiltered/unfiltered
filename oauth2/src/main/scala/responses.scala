package unfiltered.oauth2

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.2
 * For example, the authorization server redirects the user-agent by
 *  sending the following HTTP response:
 *
 *     HTTP/1.1 302 Found
 *     Location: https://client.example.com/cb?code=SplxlOBeZQQYbYS6WxSbIA
 *               &state=xyz
 */
trait OAuthResponse
sealed trait AuthorizationResponse extends OAuthResponse
sealed trait AccessResponse extends OAuthResponse

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.2
 */
case class AuthorizationCodeResponse(
  code: String,
  state: Option[String]
) extends AuthorizationResponse

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.4
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.2.2
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-5.1
 *
 * For example:
 *
 *     HTTP/1.1 200 OK
 *     Content-Type: application/json;charset=UTF-8
 *     Cache-Control: no-store
 *     Pragma: no-cache
 *     {
 *       "access_token":"2YotnFZFEjr1zCsicMWpAA",
 *       "token_type":"example",
 *       "expires_in":3600,
 *       "refresh_token":"tGzv3JOkF0XG5Qx2TlKWIA",
 *       "example_parameter":"example_value"
 *     }
 */
case class AccessTokenResponse(
  accessToken: String,
  tokenType: Option[String],
  expiresIn: Option[Int],
  refreshToken: Option[String],
  scope: Seq[String],
  state: Option[String],
  extras: Iterable[(String, String)]
) extends AccessResponse with AuthorizationResponse

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.2.2
 */
case class ImplicitAccessTokenResponse(
  accessToken: String,
  tokenType: Option[String],
  expiresIn: Option[Int],
  scope: Seq[String],
  state: Option[String],
  extras: Iterable[(String,String)]
) extends AuthorizationResponse

case class AuthorizedPass(
  owner: String,
  scope: Seq[String]
) extends OAuthResponse

case class ServiceResponse[T](
  handler: unfiltered.response.ResponseFunction[T]
) extends AuthorizationResponse

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.2.1
 *
 * For example, the authorization server redirects the user-agent by
 * sending the following HTTP response:
 *
 *  HTTP/1.1 302 Found
 *  Location: https://client.example.com/cb?error=access_denied&state=xyz
 *
 * Or, another example:
 *
 *  HTTP/1.1 400 Bad Request
 *  Content-Type: application/json;charset=UTF-8
 *  Cache-Control: no-store
 *  Pragma: no-cache
 *  {
 *    "error":"invalid_request"
 *  }
 *
 */
case class ErrorResponse(
  error: String,
  desc: String,
  uri: Option[String] = None,
  state: Option[String] = None
) extends AuthorizationResponse with AccessResponse


trait Formatting {
  import java.net.URLEncoder

  private def enc(s: String) = URLEncoder.encode(s, "utf-8")

  def qstr(kvs: Iterable[(String, String)]) =
    kvs map { _ match { case (k, v) => "%s=%s" format(enc(k), enc(v)) } } mkString("&")

  // todo: use an actual json encoder
  def Json(kvs: Iterable[(String, String)]) =
    unfiltered.response.ResponseString(kvs map { _ match { case (k, v) => "\"%s\":\"%s\"".format(k,v) } } mkString(
      "{",",","}"
    )) ~> unfiltered.response.JsonContent
}
