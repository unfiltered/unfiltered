package unfiltered.oauth2

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

object OAuth2 {

  val RedirectURI = "redirect_uri"
  val ClientId = "client_id"
  val ClientSecret = "client_secret"

  val Scope = "scope"
  val State = "state"

  val GrantType = "grant_type"
  val AuthorizationCode = "authorization_code"
  val Password = "password"
  val ClientCredentials = "client_credentials"
  val RefreshToken = "refresh_token"

  val ResponseType = "response_type"
  val Code = "code"
  val TokenKey = "token"

  val Error = "error"
  val InvalidRequest = "invalid_request"
  val UnauthorizedClient = "unauthorized_client"
  val AccessDenied = "access_denied"
  val UnsupportedResponseType = "unsupported_response_type"
  val UnsupportedGrantType = "unsupported_grant_type"
  val InvalidScope = "invalid_scope"
  val ErrorDescription = "error_description"
  val ErrorURI = "error_uri"

  val AccessTokenKey = "access_token"
  val TokenType = "token_type"
  val ExpiresIn = "expires_in"

  /** in an oauthorized request, this represents resource owner's identity */
  val XAuthorizedIdentity = "X-Authorized-Identity"
  /** in an oauthorized request, this represents the scopes of data acess available */
  val XAuthorizedScopes = "X-Authorized-Scopes"
}

trait Client {
  def id: String
  def secret: String
  def redirectURI: String
}

trait OAuthPaths {
  val AuthorizePath: String
  val AccessPath: String
}

trait DefaultOAuthPaths extends OAuthPaths {
  val AuthorizePath = "/authorize"
  val AccessPath = "/token"
}

trait Helpers {
  def qstr(kvs: Seq[(String, String)]) = kvs map { _ match { case (k, v) => k + "=" + v } } mkString("&")
  def jsbody(kvs: Seq[(String, String)]) = kvs map { _ match { case (k, v) => k + ":"+ v } } mkString("{", ",","}")
}

trait OAuthed extends AuthorizationServerProvider with unfiltered.filter.Plan with OAuthPaths with Helpers {
  import QParams._
  import OAuth2._

  implicit def s2qs(uri: String) = new {
     def ?(qs: String) = "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  def requiredMsg(what: String) = "%s is required."

  def intent = {
    case req @ Path(AuthorizePath) & Params(params) =>
      val expected = for {
        respType <- lookup(ResponseType) is required(requiredMsg(ResponseType))
        clientId <- lookup(ClientId) is required(requiredMsg(ClientId))
        secret <- lookup(ClientSecret) is optional[String, String]
        redirectURI <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        scope  <- lookup(Scope) is optional[String, String]
        state <- lookup(State) is optional[String, String]
      } yield {
         val ruri = redirectURI.get
         respType.get match {
           case Code =>
             authSvr(AuthorizationCodeRequest(req, respType.get, clientId.get, redirectURI.get, scope.get, state.get)) match {
               case HostResponse(resp) => resp
               case AuthorizationCodeResponse(code, state) =>
                  Redirect(ruri ? "code=%s%s" format(code, state.map("&state=%s".format(_)).getOrElse("")))
               case ErrorResponse(error, desc, euri, state) =>
                 val qs = qstr(
                    (Error -> error) :: (ErrorDescription -> desc) :: Nil ++
                    euri.map(ErrorURI -> (_:String)) ++
                    state.map(State -> _)
                 )
                 Redirect(ruri ? qs)
               case _ => error("invalid response")
             }
           case TokenKey =>
             authSvr(ImplicitAuthorizationRequest(req, respType.get, clientId.get, redirectURI.get, scope.get, state.get)) match {
               case HostResponse(resp) => resp
               case  AccessTokenResponse(accessToken, tokenType,  expiresIn, refreshToken/*?*/, scope, state) =>
                 val frag = qstr(
                     (AccessTokenKey -> accessToken) :: (TokenType -> tokenType) :: Nil ++
                     expiresIn.map(ExpiresIn -> (_:Int).toString) ++
                     refreshToken.map(RefreshToken -> _) ++
                     scope.map(Scope -> _) ++ state.map(State -> _ )
                 )
                 Redirect("%s#%s" format(ruri, frag))
               case ErrorResponse(error, desc, euri, state) =>
                 val qs = qstr(
                    ((Error -> error) :: (ErrorDescription -> desc) :: Nil) ++
                    euri.map(ErrorURI -> (_:String)) ++
                    state.map(State -> _)
                 )
                 Redirect(ruri ? qs)
               case _ => error("invalid response")
             }
           case _ =>
             val qs = qstr(
               (Error -> UnsupportedResponseType) :: Nil
             )
             Redirect(ruri ? qs)
         }
      }
      expected(params) orFail { errs =>
         ResponseString(errs.map { _.error } mkString(". "))
      }

    case POST(Path(AccessPath)) & Params(params) =>
      val expected = for {
        grantType <- lookup(GrantType) is required(requiredMsg(GrantType))
        code <- lookup(Code) is required(requiredMsg(Code))
        redirectURI <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        clientId <- lookup(ClientId) is required(requiredMsg(ClientId))
        clientSecret <- lookup(ClientSecret) is required(requiredMsg(ClientSecret))
      } yield {
        val ruri = redirectURI.get
        grantType.get match {
          case AuthorizationCode =>
            authSvr(AccessTokenRequest(grantType.get, code.get, ruri, clientId.get, clientSecret.get)) match {
              case HostResponse(resp) => resp
              case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, state) =>
                ResponseString(
                  jsbody(
                    (AccessTokenKey -> accessToken) :: (TokenType -> kind) :: Nil ++
                    expiresIn.map (ExpiresIn -> (_:Int).toString) ++ refreshToken.map (RefreshToken -> _)
                  )
                ) ~> CacheControl("no-store") ~> JsonContent
              case ErrorResponse(error, desc, euri, state) =>
                ResponseString(
                  jsbody(
                    (Error -> error) :: (ErrorDescription -> desc) :: Nil ++
                    euri.map (ErrorURI -> (_: String)) ++
                    state.map (State -> _)
                  )
                ) ~> BadRequest ~> CacheControl("no-store") ~> JsonContent
            }
          case _ =>
            ResponseString(
              jsbody(
                (Error -> UnsupportedGrantType) :: Nil
              )
            ) ~> BadRequest ~> CacheControl("no-store") ~> JsonContent
        }
      }
      expected(params) orFail { errs =>
         ResponseString(errs.map { _.error } mkString(". "))
      }
  }
}

trait AuthorizationServerProvider { val authSvr: AuthorizationServer }

/** Request for obtaining an authorization grant */
sealed trait AuthorizationRequest
trait OAuthResponse
sealed trait AuthorizationResponse extends OAuthResponse
sealed trait AccessRequest
sealed trait AccessResponse extends OAuthResponse

case class AuthorizationCodeRequest[T](req: Req[T], responseType: String, clientId: String, redirectURI: String, scope: Option[String], state: Option[String]) extends AuthorizationRequest
case class ImplicitAuthorizationRequest[T](req: Req[T], responseType: String, clientId: String, redirectURI: String, scope: Option[String], state: Option[String]) extends AuthorizationRequest
// PUNT: case class OwnerCredsAuthorizationRequest(user: String, password: String) extends AuthorizationRequest
case class ClientCredsAuthorizationRequest(clientId: String, secret: String, scope: Option[String]) extends AuthorizationRequest

case class AuthorizationCodeResponse(code: String, state: Option[String]) extends AuthorizationResponse

case class AccessTokenRequest(grantType: String, code: String, redirectURI: String, clientId: String, clientSecret:String) extends AccessRequest
case class ClientCredsAccessTokenRequest(grantType: String, clientId: String, secret: String, scope: Option[String]) extends AccessRequest
case class RefreshTokenRequest(grantType: String, refreshToken: String, scope: Option[String]) extends AccessRequest

case class AccessTokenResponse(accessToken: String, tokenType: String, expiresIn: Option[Int], refreshToken: Option[String], scope: Option[String], state: Option[String]) extends AccessResponse with AuthorizationResponse
/** Represents confirmation that the request has been authorized */
case class AuthorizedPass(owner: String, scope: Option[String]) extends OAuthResponse
/** The host servers custom response*/
case class HostResponse[T](rf: unfiltered.response.ResponseFunction[T]) extends AuthorizationResponse with AccessResponse

case class ErrorResponse(error: String, desc: String, uri: Option[String], state: Option[String]) extends AuthorizationResponse with AccessResponse

trait ClientStore {
  def client(clientId: String, secret: Option[String]): Option[Client]
}

trait ResourceOwner {
  def id: String
}

trait Token {
  def code: String
  def refresh: Option[String]
  def expiresIn: Option[Int]
  def clientId: String
  def redirectURI: String
  def scopes: Option[String]
  def owner: String
  def tokenType: String
}

trait TokenStore {
  def token(code: String): Option[Token]
  def clientToken(clientId: String): Option[Token]
  def accessToken(code: String): Option[Token]
  def generateAccessToken(other: Token): Token
  /** @return a short lived Token bound to a client and redirect uri for a given resource owner. */
  def generateCodeToken(owner: ResourceOwner, client: Client, redirectURI: String): Token
  def generateImplicitAccessToken(owner: ResourceOwner, client: Client, redirectURI: String): Token
}

trait HostResponses {
  /** @return a function that provides a means of logging a user in */
  def login(token: String): ResponseFunction[Any]
  /** @return a function that provides a user with a means of confirming the user's denial was processed */
  def deniedConfirmation(consumer: Client): ResponseFunction[Any]
  /** @return a function that provides a user with a means of asking for accepting a consumer's
   *    request for access to their private resources */
  def requestAcceptance(token: String, responseType: String, consumer: Client, scope: Option[String]): ResponseFunction[Any]
  /** @return a function that provides a user notification that a provided redirect uri was invalid */
  def invalidRedirectUri(uri: String, client: Client): ResponseFunction[Any]
}

trait Host extends HostResponses {
  def currentUser: Option[ResourceOwner]
    /** @return true if app logic determines this request was accepted, false otherwise */
  def accepted[T](r: Req[T]): Boolean
  /** @return true if app logic determines this request was denied, false otherwise */
  def denied[T](r: Req[T]): Boolean
  def validScopes[T](r: Req[T], scopes: Option[String]): Boolean
}

trait AuthorizationServer { self: ClientStore with TokenStore with Host =>
  import OAuth2._

  val InvalidRedirectURIMsg = "invalid redirect_uri"
  val UnknownClientMsg = "unknown client"

  def apply(r: AuthorizationRequest): AuthorizationResponse = r match {
    case AuthorizationCodeRequest(req, rt, cid, ruri, scope, state) =>
      client(cid, None) match {
        case Some(client) =>
          if(client.redirectURI != ruri) HostResponse(invalidRedirectUri(ruri, client))
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
          if(client.redirectURI != ruri) HostResponse(invalidRedirectUri(ruri, client))
          else {
            currentUser match {
              case Some(owner) =>
                if(denied(req)) HostResponse(deniedConfirmation(client))
                else if(accepted(req)) {
                  val at = generateImplicitAccessToken(owner, client, ruri)
                  AccessTokenResponse(at.code, "access", at.expiresIn, at.refresh, scope, state)
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
          if(client.redirectURI != ruri) HostResponse(invalidRedirectUri(ruri, client))
          else  {
            token(code) match {
              case Some(token) =>
                if(token.clientId != client.id || token.redirectURI != ruri)
                  ErrorResponse(UnauthorizedClient, "client not authorized", None, None)
                else {
                  val at = generateAccessToken(token)
                  AccessTokenResponse(at.code, "access", at.expiresIn, at.refresh, None, None)
                }
              case _ => ErrorResponse(InvalidRequest, "unknown code", None, None)
            }
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, None)
      }
    case ClientCredsAccessTokenRequest(gt, cid,  sec, scope) =>
      client(cid, Some(sec)) match {
        case Some(client) =>
          clientToken(client.id) match {
            case Some(token) => ErrorResponse(InvalidRequest, "TODO", None, scope)
            case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
          }
        case _ => ErrorResponse(InvalidRequest, UnknownClientMsg, None, scope)
      }
  }
}
