package unfiltered.oauth2

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

object OAuth2 {

  val RedirectURI = "redirect_uri"
  val ClientId = "client_id"
  val ClientSecret = "client_secret"

  val Scope = "scope" // optional space delim strings
  val State = "state" // optional opaque string

  val GrantType = "grant_type"
  // webserver flow
  val AuthorizationCode = "authorization_code"
  // owner creds flow
  val Password = "password"
  // client creds flow
  val ClientCredentials = "client_credentials"
  // refresh token flow
  val RefreshToken = "refresh_token"

  val ResponseType = "response_type"
  // webserver flow,
  val Code = "code"
  // user-agent flow
  val TokenKey = "token"

  val Error = "error"
  val InvalidRequest = "invalid_request"
  val UnauthorizedClient = "unauthorized_client"
  val AccessDenied = "access_denied"
  val UnsupporedResponseType = "unsupported_response_type"
  val InvalidScope = "invalid_scope"

  val ErrorDesc = "error_description"
  val ErrorURI = "error_uri"

  val AccessTokenKey = "access_token"
  val TokenType = "token_type"
  val ExpiresIn = "expires_in"


}

trait Client {
  def id: String
  def secret: String
  def redirectURI: String
}

/*
 * auth grant
 * - code
 * - implicit
 * - resource owner password creds
 * - client creds
 * - refresh token*
 *
 *
 */
trait OAuthed extends AuthorizationServerProvider with unfiltered.filter.Plan {
  import QParams._
  import OAuth2._

  val AuthPath = "/authorization"
  val TokenPath = "/token"
  def intent = {
    case GET(Path(AuthPath)) & Params(params) =>
      val expected = for {
        responseType <- lookup(ResponseType) is required("%s is required" format ResponseType)
        clientId <- lookup(ClientId) is required("%s is required" format ClientId)
        secret <- lookup(ClientSecret) is optional[String]
        redirectURI <- lookup(RedirectURI) is required("%s is required" format redirectURI)
        scope  <- lookup(Scope) is optional[String, String]
        state <- lookup(State) is optional[String, String]
      } yield {
         responseType.get match {
           case Code =>
             authSvr(AuthorizationCodeRequest(
               responseType.get, clientId.get, redirectURI.get, scope, state
             )) match {
               case _ => Pass
             }
           case TokenKey =>
             authSvr(ImplicitAuthorizationRequest(
               responseType.get, clientId.get, redirectURI.get, scope, state
             )) match {
               case _ => Pass
             }
         }
      }
      expected(params) orFail { errs =>
         ResponseString(errs.map { _.error } mkString(". "))
      }
    case POST(Path(TokenPath)) & Params(params) =>
      val expected = for {
        grantType <- lookup(GrantType) is required("%s is required" format GrantType)
      } yield {
        Pass
      }
      expected(params) orFail { errs =>
         ResponseString(errs.map { _.error } mkString(". "))
      }
  }
}


trait Protected extends ResourceServerProvider with unfiltered.filter.Plan {
  import OAuth2._
  import QParams._

  def intent = {
    case req @ Params(params) =>
      val expected = for {
        token <- lookup(AccessToken) is required("%s is required" format AccessToken)
        scopes <- lookup(Scope) is optional[String]
      } yield {
        rsrcSvr(req, token, scopes) match {
           case _ => Pass
        }
      }
      expected(params) orFail { errs =>
        ResponseString(errs.map { _.error } mkString(". "))
      }
  }
}

trait AuthorizationServerProvider {
  val authSvr: AuthorizationServer
}

trait ResourceServerProvider {
  val rsrcSvr: ResourceServer
}

sealed trait AuthorizationRequest

// web server flow
// responseType must be set to `code` for web flow
case class AuthorizationCodeRequest(responseType: String, clientId: String, redirectURI: String,
                                    scope: Option[Seq[String]], state: Option[String]) extends AuthorizationRequest
// responseType must be set to "token"
case class ImplicitAuthorizationRequest(responseType: String, clientId: String, redirectURI: String,
                                        scope: Option[Seq[String]], state: Option[String]) extends AuthorizationRequest
// PUNT case class OwnerCredsAuthorizationRequest(user: String, password: String) extends AuthorizationRequest
case class ClientCredsAuthorizationRequest(clientId: String, secret: String) extends AuthorizationRequest

sealed trait OAuthResponse
trait AuthorizationResponse extends OAuthResponse
case class ErrorResponse(error: String, desc: String, uri: Option[String],
                         state: Option[String]) extends OAuthResponse
case class AuthorizationCodeResponse(code: String, state: Option[String]) extends AuthorizationResponse

sealed trait AccessRequest

// web server flow via POST
// grantType must be set to `authorization_code` for web flow
// grantType must be set to `password` for ownerCreds flow
case class AccessTokenRequest(grantType: String, code: String, redirectURI: String,
                              clientId: String, clientSecret:String) extends AccessRequest

// only applicable to `client_credentials` flow
case class ClientCredsAccessTokenRequest(grantType: String, clientId: String,
                                       secret: String, scope: Option[Seq[String]]) extends AccessRequest

// grantType must be set to `refresh_token`
case class RefreshTokenRequest(grantType: String, refreshToken: String, scope: Option[Seq[String]]) extends AccessRequest

case class AccessTokenResponse(accessToken: String, tokenType: String, expiresIn: Option[Int],
                               refreshToken: Option[String]) extends OAuthResponse

case object AuthorizedPass extends OAuthResponse

trait ClientStore {
  def client(clientId: String): Option[Client]
  def client(clientId: String, secret: String): Option[Client]
}

trait ResourceOwner {
  def id: String
}

trait Token {
  def code: String
  def refresh: String
  def expiresIn: Int // seconds
  def clientId: String
  def scopes: String
  def owner: String
  def tokenType: String
}

trait TokenStore {
  def token(code: String): Option[Token]
  def clientToken(clientId: String): Option[Token]
  def generateAccessToken(other: Token): Token
  def generateCodeToken(owner: ResourceOwner, client: Client): Token
  def generateImplicitAccessToken(ownder: ResourceOwner, client: Client): Token
}

trait Host {
  def currentUser: ResourceOwner
}

trait AuthorizationServer { self: ClientStore with TokenStore with Host =>
  import OAuth2._

  // authorization
  def apply(r: AuthorizationRequest): OAuthResponse = r match {
    case AuthorizationCodeRequest(rt, cid, ruri, scope, state) =>
      client(cid) match {
        case Some(client) =>
          if(client.redirectURI != ruri) ErrorResponse(InvalidRequest, "invalid redirect_uri", None, state)
          else {
            // ask resource owner permission
            val c = generateCodeToken(currentUser, client)
            AuthorizationCodeResponse(c.code, state)
          }
        case _ => ErrorResponse(InvalidRequest, "unknown client", None, state)
      }
    case ImplicitAuthorizationRequest(rt, cid, ruri, scope, state) =>
      client(cid) match {
        case Some(client) =>
          if(client.redirectURI != ruri) ErrorResponse(InvalidRequest, "invalid redirect_uri", None, state)
          else {
            // ask resource owner for permission
             val at = generateImplicitAccessToken(currentUser, client)
             AccessTokenResponse(at.code, "access", Some(at.expiresIn), Some(at.refresh), scope, state)
          }
      }
  }

  // access token
  def apply(e: AccessTokenRequest): OAuthResponse = r match {
    case AccessTokenRequest(gt, code, ruri, cid, csec) =>
      client(cid, csec) match {
        case Some(client) =>
          if(client.redirectURI != ruri) ErrorResponse(InvalidRequest, "invalid redirect_uri", None, None)
          else  {
            token(code) match {
              case Some(token) =>
                if(token.clientId != client.id) ErrorResponse(UnauthorizedClient, "client not authorized", None, None)
                else {
                  val at = generateAccessToken(token)
                  AccessTokenResponse(at.code, "access", Some(at.expiresIn), Some(at.refresh))
                }
              case _ => ErrorResponse(InvalidRequest, "unknown code", None, None)
            }
          }
        case _ => ErrorResponse(InvalidRequest, "unknown client", None, None)
      }
    case ClientCredsAccessTokenRequest(cid, sec, scope) =>
      client(cid, sec) match {
        case Some(client) =>
          clientToken(client.id) match {
            case Some(token) =>
            case _ => ErrorResponse(InvalidRequest, "unknown client", None, scope)
          }
        case _ => ErrorResponse(InvalidRequest, "unknown client", None, scope)
      }
  }
}


trait ResourceServer {
  import OAuth2._
  def validScope[T](r: Req[T], scopes: Option[Seq[String]]): Boolean = true
  def apply[T](r: Req[T], token: String, scopes: Option[Seq[String]]): OAuthResponse =
    accessToken(token) match {
      case Some(at) =>
        if(!validScope(r, scopes)) ErrorResponse(InvalidRequest, "invalid scope", None, None)
        else {
          AuthorizedPass
        }
      case _ => ErrorResponse(InvalidRequest, "invalid token", None, None)
    }
}
