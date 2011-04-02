
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

trait OAuthPaths {
  val AuthorizePath: String
  val AccessPath: String
}

trait DefaultOAuthPaths extends OAuthPaths {
  val AuthorizePath = "/authorize"
  val AccessPath = "/token"
}

case class OAuth(val authSvr: AuthorizationServer) extends OAuthed with DefaultOAuthPaths

trait OAuthed extends AuthorizationServerProvider
  with unfiltered.filter.Plan with OAuthPaths with Formatting {
  import QParams._
  import OAuth2._

  implicit def s2qs(uri: String) = new {
     def ?(qs: String) = "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  def requiredMsg(what: String) = "%s is required." format what

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
             authSvr(AuthorizationCodeRequest(
               req, respType.get, clientId.get, redirectURI.get, scope.get, state.get
             )) match {
               case HostResponse(resp) => resp
               case AuthorizationCodeResponse(code, state) =>
                  Redirect(ruri ? "code=%s%s".format(
                    code, state.map("&state=%s".format(_)).getOrElse(""))
                  )
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
             authSvr(ImplicitAuthorizationRequest(
                 req, respType.get, clientId.get, redirectURI.get, scope.get, state.get
             )) match {
               case HostResponse(resp) => resp
               case ImplicitAccessTokenResponse(
                 accessToken, tokenType,  expiresIn, scope, state
               ) =>
                 val frag = qstr(
                     (AccessTokenKey -> accessToken) :: (TokenType -> tokenType) :: Nil ++
                     expiresIn.map(ExpiresIn -> (_:Int).toString) ++
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
        code <- lookup(Code) is required(requiredMsg(Code))
        redirectURI <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        clientId <- lookup(ClientId) is required(requiredMsg(ClientId))
        clientSecret <- lookup(ClientSecret) is required(requiredMsg(ClientSecret))
      } yield {
        val ruri = redirectURI.get
        grantType.get match {
          case AuthorizationCode =>
            authSvr(AccessTokenRequest(
              grantType.get, code.get, ruri, clientId.get, clientSecret.get)
            ) match {
              case HostResponse(resp) => resp
              case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, state) =>
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

trait Protected extends ResourceServerProvider with unfiltered.filter.Plan with Formatting {
  import OAuth2._
  import QParams._

  def intent = {

    case req @ Params(params) =>
      val expected = for {
        token <- lookup(AccessTokenKey) is required("%s is required" format AccessTokenKey)
        scope <- lookup(Scope) is optional[String, String]
      } yield {
        rsrcSvr(req, token.get, scope.get) match {
          case AuthorizedPass(owner, scopes) =>
            req.underlying.setAttribute(XAuthorizedIdentity, owner)
            req.underlying.setAttribute(XAuthorizedScopes, scopes.getOrElse(""))
            Pass
          case ErrorResponse(error, desc, euri, state) =>
            ResponseString(
              jsbody(
                (Error -> error) :: (ErrorDescription -> desc) :: Nil ++
                euri.map (ErrorURI -> (_: String)) ++
                state.map (State -> _)
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

trait ResourceOwner {
  def id: String
}
