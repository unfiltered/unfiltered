package unfiltered.oauth2

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

object OAuthorization {

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

trait AuthorizationEndpoints {
  val AuthorizePath: String
  val AccessPath: String
}

trait DefaultAuthorizationPaths extends AuthorizationEndpoints {
  val AuthorizePath = "/authorize"
  val AccessPath = "/token"
}

trait ValidationMessages {
  def requiredMsg(what: String): String
}

trait DefaultValidationMessages extends ValidationMessages {
  def requiredMsg(what: String) = "%s is required" format what
}

class OAuthorization(val authSvr: AuthorizationServer) extends Authorized
     with DefaultAuthorizationPaths with DefaultValidationMessages

trait Authorized extends AuthorizationServerProvider
  with unfiltered.filter.Plan with AuthorizationEndpoints
  with Formatting with ValidationMessages {
  import QParams._
  import OAuthorization._

  implicit def s2qs(uri: String) = new {
     def ?(qs: String) = "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  def intent = {

    case req @ Path(AuthorizePath) & Params(params) =>
      val expected = for {
        responseType <- lookup(ResponseType) is required(requiredMsg(ResponseType))
        clientId <- lookup(ClientId) is required(requiredMsg(ClientId))
        secret <- lookup(ClientSecret) is optional[String, String]
        redirectURI <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        scope  <- lookup(Scope) is optional[String, String]
        state <- lookup(State) is optional[String, String]
      } yield {

         val ruri = redirectURI.get
         responseType.get match {
           case Code =>
             println("auth request for code")
             authSvr(AuthorizationCodeRequest(
               req, responseType.get, clientId.get,
               redirectURI.get, scope.get, state.get)) match {
               case HostResponse(resp) => resp
               case AuthorizationCodeResponse(code, state) =>
                  println("got auth code")
                  Redirect(ruri ? "code=%s%s".format(
                    code, state.map("&state=%s".format(_)).getOrElse(""))
                  )
               case ErrorResponse(error, desc, euri, state) =>
                 println("error getting oauth code")
                 val qs = qstr(
                    (Error -> error) :: (ErrorDescription -> desc) :: Nil ++
                    euri.map(ErrorURI -> (_:String)) ++
                    state.map(State -> _)
                 )
                 Redirect(ruri ? qs)
               case _ => error("invalid response")
             }
           case TokenKey =>
             println("auth request for token")
             authSvr(ImplicitAuthorizationRequest(
                 req, responseType.get, clientId.get,
                 redirectURI.get, scope.get, state.get)) match {
               case HostResponse(resp) => resp
               case ImplicitAccessTokenResponse(
                 accessToken, tokenType,
                 expiresIn, scope, state) =>
                 println("implicit token granted")
                 val frag = qstr(
                     (AccessTokenKey -> accessToken) :: (TokenType -> tokenType) :: Nil ++
                     expiresIn.map(ExpiresIn -> (_:Int).toString) ++
                     scope.map(Scope -> _) ++ state.map(State -> _ )
                 )
                 Redirect("%s#%s" format(ruri, frag))
               case ErrorResponse(error, desc, euri, state) =>
                 println("error response for implicit token request")
                 val qs = qstr(
                    ((Error -> error) :: (ErrorDescription -> desc) :: Nil) ++
                    euri.map(ErrorURI -> (_:String)) ++
                    state.map(State -> _)
                 )
                 Redirect(ruri ? qs)
               case _ => error("invalid response")
             }
           case unsupported =>
             println("unsupported response type %s" format unsupported)
             val qs = qstr(
               (Error -> UnsupportedResponseType) :: Nil
             )
             Redirect(ruri ? qs)
         }
      }

      expected(params) orFail { errs =>
        ResponseString(
          jsbody(
            (Error -> InvalidRequest) ::
            (ErrorDescription -> errs.map { _.error }.mkString(", ")) :: Nil
          )
        ) ~> BadRequest ~> CacheControl("no-store") ~> JsonContent
      }

    case POST(Path(AccessPath)) & Params(params) =>
      val expected = for {

        grantType <- lookup(GrantType) is required(requiredMsg(GrantType))
        code <- lookup(Code) is optional[String, String] // req when access
        redirectURI <- lookup(RedirectURI) is optional[String, String] // req when access/opt when refresh
        clientId <- lookup(ClientId) is required(requiredMsg(ClientId))
        clientSecret <- lookup(ClientSecret) is required(requiredMsg(ClientSecret))
        refreshToken <- lookup(RefreshToken) is optional[String, String] // req when access
        scope <- lookup(Scope) is optional[String, String]

      } yield {

        val ruri = redirectURI.get
        grantType.get match {
          case RefreshToken =>
            refreshToken.get match {
              case Some(rtoken) =>
                authSvr(RefreshTokenRequest(
                  grantType.get, rtoken,
                  clientId.get, clientSecret.get, scope.get)) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn,
                                           refreshToken, scope, state) =>
                                             ResponseString(
                                               jsbody(
                                                 (AccessTokenKey -> accessToken) :: (TokenType -> kind) :: Nil ++
                                                 expiresIn.map (ExpiresIn -> (_:Int).toString) ++
                                                 refreshToken.map (RefreshToken -> _)
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
              case _ => Ok
            }
          case AuthorizationCode =>
            (code.get, ruri) match {
              case (Some(c), Some(r)) =>
                authSvr(AccessTokenRequest(
                  grantType.get, c, r,
                  clientId.get, clientSecret.get)) match {
                  case HostResponse(resp) => resp
                  case AccessTokenResponse(accessToken, kind, expiresIn,
                                           refreshToken, scope, state) =>
                      ResponseString(
                        jsbody(
                            (AccessTokenKey -> accessToken) :: (TokenType -> kind) :: Nil ++
                            expiresIn.map (ExpiresIn -> (_:Int).toString) ++
                            refreshToken.map (RefreshToken -> _)
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
                    (Error -> InvalidRequest) ::
                    (ErrorDescription -> (requiredMsg(Code) :: requiredMsg(RedirectURI) :: Nil).mkString(" and ")) ::
                    Nil
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
        ResponseString(
          jsbody(
            (Error -> InvalidRequest) ::
            (ErrorDescription -> errs.map { _.error }.mkString(", ")) :: Nil
          )
        ) ~> BadRequest ~> CacheControl("no-store") ~> JsonContent
      }
  }
}

