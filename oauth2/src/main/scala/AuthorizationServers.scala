package unfiltered.oauth2

trait AuthorizationServerProvider { val authSvr: AuthorizationServer }

object AuthorizationServer {
  val InvalidRedirectURIMsg = "invalid redirect_uri"
  val UnknownClientMsg = "unknown client"
}

trait AuthorizationServer {
  self: ClientStore with TokenStore with Host =>
  import OAuthorization._
  import AuthorizationServer._

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {

    case AuthorizationCodeRequest(req, responseType, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(c) =>
          if(c.redirectUri != redirectUri) HostResponse(
            invalidRedirectUri(redirectUri, c)
          )
          else {
            resourceOwner match {
              case Some(owner) =>
                 if(denied(req)) HostResponse(deniedConfirmation(c))
                 else if(accepted(req)) {
                    val token = generateCodeToken(owner, c, scope, redirectUri)
                    AuthorizationCodeResponse(token, state)
                 }
                 else error("need to approve or deny request")
              case _ => HostResponse(login("?"))
            }

          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, state)
      }

    case ImplicitAuthorizationRequest(req, responseType, clientId, redirectUri, scope, state) =>
      client(clientId, None) match {
        case Some(c) =>
          if(c.redirectUri != redirectUri) HostResponse(
            invalidRedirectUri(redirectUri, c)
          )
          else {
            resourceOwner match {
              case Some(owner) =>
                if(denied(req)) HostResponse(deniedConfirmation(c))
                else if(accepted(req)) {
                  val t = generateImplicitAccessToken(owner, c, scope, redirectUri)
                  ImplicitAccessTokenResponse(
                    t.value, t.tokenType, t.expiresIn, scope, state
                  )
                }
                else error("need to approve or deny request")
             case _ => HostResponse(login("?"))
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, state)
      }
  }

  def apply(r: AccessRequest): AccessResponse = r match {

    case AccessTokenRequest(grantType, code, redirectUri, clientId, clientSecret) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
          if(c.redirectUri != redirectUri) HostResponse(
            invalidRedirectUri(redirectUri, c)
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
    case RefreshTokenRequest(grantType, refreshToken, clientId, clientSecret, scope) =>
        client(clientId, Some(clientSecret)) match {
          case Some(c) =>
             clientToken(c.id) match {
               case Some(t) =>
                 if(t.refresh == refreshToken) {
                   val r = refresh(t)
                   AccessTokenResponse(
                     t.value, t.tokenType, t.expiresIn, t.refresh, None, scope
                   )
                 }
                 else ErrorResponse(UnauthorizedClient, "...", None, scope)
                 case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
             }
          case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
        }
    case ClientCredsAccessTokenRequest(grantType, clientId, clientSecret, scope) =>
      client(clientId, Some(clientSecret)) match {
        case Some(c) =>
          clientToken(c.id) match {
            case Some(token) => ErrorResponse(
              InvalidRequest, "TODO", None, scope
            )
            case _ => ErrorResponse(
              InvalidRequest, UnknownClientMsg, None, scope
            )
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
      }
  }
}
