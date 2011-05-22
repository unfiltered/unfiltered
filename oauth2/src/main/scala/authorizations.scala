package unfiltered.oauth2

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}
import unfiltered.filter.request.ContextPath // work on removing this dep

object OAuthorization {

  val RedirectURI = "redirect_uri"
  val ClientId = "client_id"
  val ClientSecret = "client_secret"

  val Scope = "scope"
  val State = "state"

  val GrantType = "grant_type"
  val AuthorizationCode = "authorization_code"
  val PasswordType = "password"
  val Password = "password"
  val Username = "username"
  val ClientCredentials = "client_credentials"
  val OwnerCredentials = "password"
  val RefreshToken = "refresh_token"

  val ResponseType = "response_type"
  val Code = "code"
  val TokenKey = "token"

  val Error = "error"
  val InvalidClient = "invalid_client"
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
}

/** Paths for authorization and token access */
trait AuthorizationEndpoints {
  val AuthorizePath: String
  val TokenPath: String
}

trait DefaultAuthorizationPaths extends AuthorizationEndpoints {
  val AuthorizePath = "/authorize"
  val TokenPath = "/token"
}

/** Customized parameter validation message */
trait ValidationMessages {
  def requiredMsg(what: String): String
}

trait DefaultValidationMessages extends ValidationMessages {
  def requiredMsg(what: String) = "%s is required" format what
}

/** Configured Authorization server */
case class OAuthorization(val auth: AuthorizationServer) extends Authorized
     with DefaultAuthorizationPaths with DefaultValidationMessages

trait Authorized extends AuthorizationProvider
  with unfiltered.filter.Plan with AuthorizationEndpoints
  with Formatting with ValidationMessages {
  import QParams._
  import OAuthorization._

  implicit def s2qs(uri: String) = new {
     def ?(qs: String) = "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  protected def accessResponder(accessToken: String, kind: String,
                      expiresIn: Option[Int], refreshToken: Option[String],
                      scope: Option[String]): ResponseFunction[Any] =
    Json((AccessTokenKey -> accessToken) :: (TokenType -> kind) :: Nil ++
        expiresIn.map (ExpiresIn -> (_:Int).toString) ++
        refreshToken.map (RefreshToken -> _) ++
        scope.map(Scope -> _)) ~> CacheControl("no-store")

  protected def errorResponder(error: String, desc: String, euri: Option[String],
                               state: Option[String]): ResponseFunction[Any] =
    Json((Error -> error) :: (ErrorDescription -> desc) :: Nil ++
        euri.map (ErrorURI -> (_: String)) ++
        state.map (State -> _)) ~> BadRequest ~> CacheControl("no-store")

  def intent = {

    case req @ ContextPath(_, AuthorizePath) & Params(params) =>
      val expected = for {
        responseType <- lookup(ResponseType) is required(requiredMsg(ResponseType))
        clientId     <- lookup(ClientId) is required(requiredMsg(ClientId))
        redirectURI  <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        scope        <- lookup(Scope) is optional[String, String]
        state        <- lookup(State) is optional[String, String]
      } yield {

         val ruri = redirectURI.get
         responseType.get match {

           case Code =>
             auth(AuthorizationCodeRequest(
               req, clientId.get,
               redirectURI.get, scope.get, state.get)) match {

               case ContainerResponse(resp) => resp

               case AuthorizationCodeResponse(code, state) =>
                  Redirect(ruri ? "code=%s%s".format(
                    code, state.map("&state=%s".format(_)).getOrElse(""))
                  )

               case ErrorResponse(error, desc, euri, state) =>
                 Redirect(ruri ? qstr(
                   (Error -> error) ::
                   (ErrorDescription -> desc) :: Nil ++
                   euri.map(ErrorURI -> (_:String)) ++
                   state.map(State -> _)
                 ))

               case _ => BadRequest
             }

           case TokenKey =>
             auth(ImplicitAuthorizationRequest(
               req, clientId.get,
               redirectURI.get, scope.get, state.get)) match {
               case ContainerResponse(resp) => resp
               case ImplicitAccessTokenResponse(
                 accessToken, tokenType,
                 expiresIn, scope, state) =>
                   val frag = qstr(
                     (AccessTokenKey -> accessToken) ::
                     (TokenType -> tokenType) :: Nil ++
                     expiresIn.map(ExpiresIn -> (_:Int).toString) ++
                     scope.map(Scope -> _) ++
                     state.map(State -> _ )
                   )
               Redirect("%s#%s" format(ruri, frag))
               case ErrorResponse(error, desc, euri, state) =>
                 Redirect("%s#%s" format(ruri, qstr(
                   ((Error -> error) :: (ErrorDescription -> desc) :: Nil) ++
                   euri.map(ErrorURI -> (_:String)) ++
                   state.map(State -> _)
                 )))
               case _ => BadRequest
             }

           case unsupported =>
             auth(IndeterminateAuthorizationRequest(
               req, unsupported, clientId.get,
               redirectURI.get, scope.get, state.get)) match {
               case ContainerResponse(cr) => cr
               case ErrorResponse(error, desc, euri, state) =>
                 Redirect(ruri ? qstr(
                   (Error -> error) ::
                   (ErrorDescription -> desc) :: Nil ++
                   euri.map(ErrorURI -> (_:String)) ++
                   state.map(State -> _)
                 ))
               case _ => BadRequest
             }
         }
      }

      expected(params) orFail { errs =>
        params(RedirectURI) match {
          case Seq(uri) =>
            val qs = qstr(
              (Error -> InvalidRequest) ::
              (ErrorDescription ->  errs.map { _.error }.mkString(", ")) ::
              Nil
            )
            params(ResponseType) match {
              case Seq(TokenKey) =>
                 // encode in fragment
                 Redirect("%s#%s" format(
                   uri, qs
                 ))
              case _ =>
                 // uncode in query string
                Redirect(uri ? qs)
            }
          case _ => auth.mismatchedRedirectUri

        }
      }

    case req @ POST(ContextPath(_, TokenPath)) & Params(params) =>
      val expected = for {
        grantType     <- lookup(GrantType) is required(requiredMsg(GrantType))
        code          <- lookup(Code) is optional[String, String]
        redirectURI   <- lookup(RedirectURI) is optional[String, String]
        clientId      <- lookup(ClientId) is required(requiredMsg(ClientId))
        clientSecret  <- lookup(ClientSecret) is required(requiredMsg(ClientSecret))
        refreshToken  <- lookup(RefreshToken) is optional[String, String]
        scope         <- lookup(Scope) is  optional[String, String]
      } yield {

        val ruri = redirectURI.get
        grantType.get match {

          case ClientCredentials =>
            auth(ClientCredentialsRequest(clientId.get,
                                          clientSecret.get,
                                          scope.get)) match {
              case AccessTokenResponse(accessToken, kind, expiresIn,
                                       refreshToken, scope, _) =>
                   accessResponder(
                     accessToken, kind, expiresIn, refreshToken, scope
                   )
              case ErrorResponse(error, desc, euri, state) =>
                errorResponder(error, desc, euri, state)
            }

          case RefreshToken =>
            refreshToken.get match {
              case Some(rtoken) =>
                auth(RefreshTokenRequest(
                  rtoken, clientId.get, clientSecret.get, scope.get)) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn,
                                           refreshToken, scope, _) =>
                     accessResponder(
                       accessToken, kind, expiresIn, refreshToken, scope
                     )
                  case ErrorResponse(error, desc, euri, state) =>
                    errorResponder(error, desc, euri, state)
                }
              case _ => errorResponder(InvalidRequest, requiredMsg(RefreshToken), None, None)
            }

          case AuthorizationCode =>
            (code.get, ruri) match {
              case (Some(c), Some(r)) =>
                auth(AccessTokenRequest(
                  c, r, clientId.get, clientSecret.get)) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn,
                                           refreshToken, scope, _) =>
                      accessResponder(
                        accessToken, kind, expiresIn, refreshToken, scope
                      )
                  case ErrorResponse(error, desc, euri, state) =>
                    errorResponder(error, desc, euri, state)
                }
              case _ =>
                errorResponder(
                  InvalidRequest,
                  (requiredMsg(Code) :: requiredMsg(RedirectURI) :: Nil).mkString(" and "),
                  None, None
                )
            }
          case unsupported =>
            errorResponder(UnsupportedGrantType, "%s is unsupported" format unsupported, None, None)
        }
      }


     val all = ((Right(params): Either[String, Map[String, Seq[String]]]) /: BasicAuth(req))((a,e) => e match {
       case (cid, csec) =>
         val pref = Right(a.right.get ++ Map(ClientId -> Seq(cid), ClientSecret-> Seq(csec)))
         a.right.get(ClientId) match {
           case Seq(id) =>
             if(id == cid) pref else Left("client ids did not match")
           case _ => pref
         }
       case _ => a
     })

     all fold({ err =>
       errorResponder(InvalidRequest, err, None, None)
     }, { mixed =>
       expected(mixed) orFail { errs =>
         errorResponder(InvalidRequest, errs.map { _.error }.mkString(", "), None, None)
       }
     })


  }
}
