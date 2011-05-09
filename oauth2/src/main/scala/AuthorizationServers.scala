package unfiltered.oauth2

trait AuthorizationProvider { val auth: AuthorizationServer }

object AuthorizationServer {
  val InvalidRedirectURIMsg = "invalid redirect_uri"
  val UnknownClientMsg = "unknown client"
}

trait AuthorizationServer {
  self: ClientStore with TokenStore with Container =>
  import OAuthorization._
  import AuthorizationServer._

  def mismatchedRedirectUri = invalidRedirectUri(None, None)

  /** Some servers may wish to override this with custom redirect_url
   *  validation rules
   * @return true if valid, false otherwise */
  def validRedirectUri(provided: String, client: Client): Boolean =
    provided.startsWith(client.redirectUri)

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {

    case AuthorizationCodeRequest(req, clientId, redirectUri, scope, state) =>
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
                    val token = generateCodeToken(owner, c, scope, redirectUri)
                    AuthorizationCodeResponse(token, state)
                 }
                 else ContainerResponse(requestAuthorization(RequestBundle(req, Code, c, Some(owner), redirectUri, scope, state)))
              case _ => ContainerResponse(login(RequestBundle(req, Code, c, None, redirectUri, scope, state)))
            }

          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, state)
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
                else ContainerResponse(requestAuthorization(RequestBundle(req, TokenKey, c, Some(owner), redirectUri,  scope, state)))
             case _ => ContainerResponse(login(RequestBundle(req, TokenKey, c, None, redirectUri, scope, state)))
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, state)
      }
  }

  def apply(r: AccessRequest): AccessResponse = r match {

    case AccessTokenRequest(code, redirectUri, clientId, clientSecret) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
          if(!validRedirectUri(redirectUri, c)) ErrorResponse(
            InvalidClient, "invalid redirect uri", None, None
          )
          else  {
            token(code) match {
              case Some(token) =>
                if(token.clientId != c.id || token.redirectUri != redirectUri)
                  ErrorResponse(
                    UnauthorizedClient, "client not authorized", None, None
                  )
                else {
                  val t = generateAccessToken(token)
                  AccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, t.refresh, None, None
                 )
                }
              case _ => ErrorResponse(
                InvalidRequest, "unknown code", None, None
              )
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, None)
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
                   UnauthorizedClient, "refresh token does not belong to client", None, scope
                 )
               case _ => ErrorResponse(InvalidRequest, "unknown request token", None, scope)
             }
          case _ => ErrorResponse(InvalidClient, UnknownClientMsg, None, scope)
        }

    case ClientCredentialsRequest(clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
           val tok = generateClientToken(c, scope)
           AccessTokenResponse(
             tok.value, tok.tokenType, tok.expiresIn, tok.refresh, None, None
           )
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
      }
  }
}
