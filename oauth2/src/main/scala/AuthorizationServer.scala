package unfiltered.oauth2

trait AuthorizationProvider { val auth: AuthorizationServer }

object AuthorizationServer {
  val InvalidRedirectURIMsg = "invalid redirect_uri"
  val UnknownClientMsg = "unknown client"
}

/**
 * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-1.1
 */
trait AuthorizationServer {
  self: ClientStore with TokenStore with Container =>
  import OAuthorization._
  import AuthorizationServer._

  def mismatchedRedirectUri = invalidRedirectUri(None, None)

  /** todo: rectify this design */
  def errUri(error: String) = errorUri(error)

  /** Some servers may wish to override this with custom redirect_url
   *  validation rules
   * @return true if valid, false otherwise */
  def validRedirectUri(provided: String, client: Client): Boolean =
    provided.startsWith(client.redirectUri)

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {

    case AuthorizationCodeRequest(req, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(client) =>
          if(!validRedirectUri(redirectUri, client)) ContainerResponse(
            invalidRedirectUri(Some(redirectUri), Some(client))
          ) else if(!validScopes(scope)) {
            ErrorResponse(InvalidScope, "invalid scope", errorUri(InvalidScope), state)
          } else {
            resourceOwner(req) match {
              case Some(owner) =>
                 if(denied(req)) ErrorResponse(AccessDenied, "user denied request", errorUri(AccessDenied), state)
                 else if(accepted(req)) {
                    val code = generateAuthorizationCode(owner, client, scope, redirectUri)
                    AuthorizationCodeResponse(code, state)
                 }
                 else ContainerResponse(requestAuthorization(RequestBundle(req, Code, client, Some(owner), redirectUri, scope, state)))
              case _ => ContainerResponse(login(RequestBundle(req, Code, client, None, redirectUri, scope, state)))
            }

          }
        case _ => ContainerResponse(invalidClient)
      }

    case ImplicitAuthorizationRequest(req, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(c) =>
          if(!validRedirectUri(redirectUri, c)) ContainerResponse(
            invalidRedirectUri(Some(redirectUri), Some(c))
          )
          else {
            resourceOwner(req) match {
              case Some(owner) =>
                if(denied(req)) ErrorResponse(AccessDenied, "user denied request", None, state)
                else if(accepted(req)) {
                  val t = generateImplicitAccessToken(owner, c, scope, redirectUri)
                  ImplicitAccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, scope, state
                  )
                }
                else ContainerResponse(requestAuthorization(RequestBundle(req, TokenKey, c, Some(owner), redirectUri, scope, state)))
             case _ => ContainerResponse(login(RequestBundle(req, TokenKey, c, None, redirectUri, scope, state)))
            }
          }
        case _ => ContainerResponse(invalidClient)
      }

    case IndeterminateAuthorizationRequest(req, responseType, clientId, redirectUri, scope, state) =>
        client(clientId, None) match {
          case Some(c) =>
            if(!validRedirectUri(redirectUri, c)) ContainerResponse(
              invalidRedirectUri(Some(redirectUri), Some(c))
            )
            else ErrorResponse(UnsupportedResponseType, "unsupported response type %s" format responseType, errorUri(UnsupportedResponseType), state)
          case _ => ContainerResponse(invalidClient)
        }
  }

  def apply(r: AccessRequest): AccessResponse = r match {

    case AccessTokenRequest(code, redirectUri, clientId, clientSecret) =>
      client(clientId, Some(clientSecret)) match {
        case Some(client) =>
          if(!validRedirectUri(redirectUri, client)) ErrorResponse(
            InvalidClient, "invalid redirect uri", None, None
          )
          else  {
            token(code) match {
              case Some(token) =>
                // tokens redirectUri must be exact match to the one provided
                if(token.clientId != client.id || token.redirectUri != redirectUri)
                  ErrorResponse(
                    UnauthorizedClient, "client not authorized", errorUri(UnauthorizedClient), None
                  )
                else {
                  val t = exchangeAuthorizationCode(token)
                  AccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, t.refresh, None, None
                 )
                }
              case _ => ErrorResponse(
                InvalidRequest, "unknown code", None, None
              )
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, errorUri(InvalidRequest), None)
      }

    case RefreshTokenRequest(rToken, clientId, clientSecret, scope) =>
        client(clientId, Some(clientSecret)) match {
          case Some(c) =>
             refreshToken(rToken) match {
               case Some(t) =>
                 if(t.clientId == clientId) {
                   val r = refresh(t)
                   AccessTokenResponse(
                     t.value, t.tokenType, t.expiresIn, t.refresh, None, scope
                   )
                 } else ErrorResponse(
                   UnauthorizedClient, "refresh token does not belong to client", errorUri(UnauthorizedClient), scope
                 )
               case _ => ErrorResponse(InvalidRequest, "unknown request token", errorUri(InvalidRequest), scope)
             }
          case _ => ErrorResponse(InvalidClient, UnknownClientMsg, errorUri(InvalidClient), scope)
        }

    case ClientCredentialsRequest(clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
           val tok = generateClientToken(c, scope)
           AccessTokenResponse(
             tok.value, tok.tokenType, tok.expiresIn, tok.refresh, None/* no scope for client */, None/* no state for client*/
           )
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, errorUri(InvalidClient), scope)
      }

    case PasswordRequest(userName, password, clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
          resourceOwner(userName, password) match {
            case Some(owner) =>
              val tok = generatePasswordToken(owner, c, scope)
              AccessTokenResponse(
                tok.value, tok.tokenType, tok.expiresIn, tok.refresh, scope, None
              )
            case None => ErrorResponse(InvalidRequest, UnauthorizedClient, errorUri(InvalidClient), scope)
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, errorUri(InvalidClient), scope)
      }
    }
}
