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
  self: ClientStore with TokenStore with Service =>
  import OAuthorization._
  import AuthorizationServer._

  def mismatchedRedirectUri = invalidRedirectUri(None, None)

  /** todo: rectify this design */
  def errUri(error: String) = errorUri(error)

  /** Some servers may wish to override this with custom redirect_url
   *  validation rules. We are being lenient here by checking the base
   *  of the registered redirect_uri. The spec recommends using the `state`
   *  param for per-request customization.
   * @return true if valid, false otherwise
   * see http://tools.ietf.org/html/draft-ietf-oauth-v2-22#section-3.1.2.2
   */
  def validRedirectUri(provided: String, client: Client): Boolean =
    !provided.contains("#") && provided.startsWith(client.redirectUri)

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {

    case AuthorizationCodeRequest(req, responseTypes, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(client) =>
          if(!validRedirectUri(redirectUri, client)) ServiceResponse(
            invalidRedirectUri(Some(redirectUri), Some(client))
          ) else if(!validScopes(scope)) {
            ErrorResponse(InvalidScope, "invalid scope", errorUri(InvalidScope), state)
          } else {
            resourceOwner(req) match {
              case Some(owner) =>
                 if(denied(req)) ErrorResponse(
                   AccessDenied, "user denied request", errorUri(AccessDenied), state)
                 else if(accepted(req)) {
                    AuthorizationCodeResponse(
                      generateAuthorizationCode(responseTypes, owner, client, scope, redirectUri),
                      state)
                 }
                 else ServiceResponse(requestAuthorization(
                   RequestBundle(req, responseTypes, client, Some(owner), redirectUri, scope, state)
                 ))
              case _ => ServiceResponse(
                login(RequestBundle(req, responseTypes, client, None, redirectUri, scope, state)))
            }

          }
        case _ => ServiceResponse(invalidClient)
      }

    case ImplicitAuthorizationRequest(req, responseTypes, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(c) =>
          if(!validRedirectUri(redirectUri, c)) ServiceResponse(
            invalidRedirectUri(Some(redirectUri), Some(c))
          )
          else {
            resourceOwner(req) match {
              case Some(owner) =>
                if(denied(req)) ErrorResponse(AccessDenied, "user denied request", None, state)
                else if(accepted(req)) {
                  val t = generateImplicitAccessToken(responseTypes, owner, c, scope, redirectUri)
                  ImplicitAccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, scope, state, t.extras
                  )
                }
                else ServiceResponse(requestAuthorization(
                  RequestBundle(req, responseTypes, c, Some(owner), redirectUri, scope, state)
                ))
             case _ => ServiceResponse(login(
               RequestBundle(req, responseTypes, c, None, redirectUri, scope, state)
             ))
            }
          }
        case _ => ServiceResponse(invalidClient)
      }

    case IndeterminateAuthorizationRequest(
      req, responseTypes, clientId, redirectUri, scope, state) =>
        client(clientId, None) match {
          case Some(c) =>
            if(!validRedirectUri(redirectUri, c)) ServiceResponse(
              invalidRedirectUri(Some(redirectUri), Some(c))
            )
            else ErrorResponse(
              UnsupportedResponseType, "unsupported response type(s) %s" format responseTypes,
              errorUri(UnsupportedResponseType), state)
          case _ => ServiceResponse(invalidClient)
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
                // in order further bind the access request to the auth request
                if(token.clientId != client.id || token.redirectUri != redirectUri)
                  ErrorResponse(
                    UnauthorizedClient, "client not authorized", errorUri(UnauthorizedClient), None
                  )
                else {
                  val t = exchangeAuthorizationCode(token)
                  AccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, t.refresh, Nil, None, t.extras
                 )
                }
              case _ => ErrorResponse(
                InvalidRequest, "unknown code", None, None
              )
            }
          }
        case _ => ErrorResponse(
          InvalidRequest, UnknownClientMsg, errorUri(InvalidRequest), None)
      }

    case RefreshTokenRequest(rToken, clientId, clientSecret, scope) =>
        client(clientId, Some(clientSecret)) match {
          case Some(c) =>
             refreshToken(rToken) match {
               case Some(t) =>
                 if(t.clientId == clientId) {
                   val r = refresh(t)
                   AccessTokenResponse(
                     r.value, r.tokenType, r.expiresIn, r.refresh, scope, None, r.extras
                   )
                 } else ErrorResponse(
                   UnauthorizedClient, "refresh token does not belong to client",
                   errorUri(UnauthorizedClient), None
                 )
               case _ => ErrorResponse(
                 InvalidRequest, "unknown request token", errorUri(InvalidRequest), None)
             }
          case _ => ErrorResponse(InvalidClient, UnknownClientMsg, errorUri(InvalidClient), None)
        }

    case ClientCredentialsRequest(clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
           val tok = generateClientToken(c, scope)
           AccessTokenResponse(
             tok.value, tok.tokenType, tok.expiresIn, tok.refresh,
             Nil/* no scope for client */, None/* no state for client*/, tok.extras
           )
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, errorUri(InvalidClient), None)
      }

    case PasswordRequest(userName, password, clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
          resourceOwner(userName, password) match {
            case Some(owner) =>
              val tok = generatePasswordToken(owner, c, scope)
              AccessTokenResponse(
                tok.value, tok.tokenType, tok.expiresIn, tok.refresh, scope, None, tok.extras
              )
            case None => ErrorResponse(
              InvalidRequest, UnauthorizedClient, errorUri(InvalidClient), None)
          }
        case _ => ErrorResponse(
          InvalidRequest, UnknownClientMsg, errorUri(InvalidClient), None)
      }
    }
}
