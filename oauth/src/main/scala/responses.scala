package unfiltered.oauth

import unfiltered.response.{ResponseWriter, Html}
import java.io.PrintWriter

trait OAuthResponse

case class ChallengeResponse(realm: String)

case class Failure(status: Int, msg: String) extends OAuthResponse

case object LoginResponse extends OAuthResponse

case class PageResponse(p: unfiltered.response.Html) extends OAuthResponse

/** writes the response of to an oauth request to response body */
trait OAuthResponseWriter extends OAuthResponse with ResponseWriter with Encoding with Combining {
  def params: Map[String, String]
  def write(writer: PrintWriter) = writer.write(combine(params))
}

/** response to the client after verifying client request */
case class TokenResponse(oauth_token: String, oauth_token_secret: String, oauth_callback_confirmed: Boolean) extends OAuthResponseWriter {
  def params = Map(
    "oauth_token" -> oauth_token, 
    "oauth_token_secret" -> oauth_token_secret, 
    "oauth_callback_confirmed" -> oauth_callback_confirmed.toString
  )
}

/** response to the client after a user authorizes a token */
case class AuthorizeResponse(oauth_callback: String, oauth_token: String, oauth_verifier: String) extends OAuthResponse

/** response to the client after verifying authorized token and client request */
case class AccessResponse(oauth_token: String, oauth_token_secret: String) extends OAuthResponseWriter {
  def params = Map(
    "oauth_token" -> oauth_token, 
    "oauth_token_secret" -> oauth_token_secret
  )
}
