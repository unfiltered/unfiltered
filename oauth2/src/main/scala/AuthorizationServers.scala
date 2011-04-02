package unfiltered.oauth2

trait AuthorizationServerProvider { val authSvr: AuthorizationServer }

object AuthorizationServer {
  val InvalidRedirectURIMsg = "invalid redirect_uri"
  val UnknownClientMsg = "unknown client"
}

trait AuthorizationServer {
  self: ClientStore with TokenStore with Host =>
  import OAuth2._
  import AuthorizationServer._

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {

    case AuthorizationCodeRequest(req, rt, cid, ruri, scope, state) =>
      client(cid, None) match {
        case Some(client) =>
          if(client.redirectURI != ruri) HostResponse(
            invalidRedirectUri(ruri, client)
          )
          else {
            currentUser match {
              case Some(owner) =>
                 if(denied(req)) HostResponse(deniedConfirmation(client))
                 else if(accepted(req)) {
                    val c = generateCodeToken(owner, client, ruri)
                    AuthorizationCodeResponse(c.code, state)
                 }
                 else error("need to approve or deny request")
              case _ => HostResponse(login("?"))
            }

          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, state)
      }

    case ImplicitAuthorizationRequest(req, rt, cid, ruri, scope, state) =>
      client(cid, None) match {
        case Some(client) =>
          if(client.redirectURI != ruri) HostResponse(
            invalidRedirectUri(ruri, client)
          )
          else {
            currentUser match {
              case Some(owner) =>
                if(denied(req)) HostResponse(deniedConfirmation(client))
                else if(accepted(req)) {
                  val at = generateImplicitAccessToken(owner, client, ruri)
                  ImplicitAccessTokenResponse(
                    at.code, "access", at.expiresIn, scope, state
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

    case AccessTokenRequest(gt, code, ruri, cid, csec) =>
      client(cid, Some(csec)) match {
        case Some(client) =>
          if(client.redirectURI != ruri) HostResponse(
            invalidRedirectUri(ruri, client)
          )
          else  {
            token(code) match {
              case Some(token) =>
                if(token.clientId != client.id || token.redirectURI != ruri)
                  ErrorResponse(
                    UnauthorizedClient, "client not authorized", None, None
                  )
                else {
                  val at = generateAccessToken(token)
                  AccessTokenResponse(
                    at.code, "access", at.expiresIn, at.refresh, None, None
                  )
                }
              case _ => ErrorResponse(
                InvalidRequest, "unknown code", None, None
              )
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, None)
      }

    case ClientCredsAccessTokenRequest(gt, cid,  sec, scope) =>
      client(cid, Some(sec)) match {
        case Some(client) =>
          clientToken(client.id) match {
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
