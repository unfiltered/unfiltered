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

  /**
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.2.1
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.2.2.1
   */
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
  /**
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3
   */
  val AuthorizePath = "/authorize"

  /**
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3
   * @see http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3.2
   */
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
  with AuthorizationEndpoints with Formatting
  with ValidationMessages with unfiltered.filter.Plan {
  import QParams._
  import OAuthorization._

  implicit def s2qs(uri: String) = new {
     def ?(qs: String) = "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  protected def accessResponder(accessToken: String, kind: String, expiresIn: Option[Int],
                                refreshToken: Option[String], scope:Seq[String], extras: Iterable[(String, String)]) =
    CacheControl("no-store") ~> Pragma("no-cache") ~> Json(Map(AccessTokenKey -> accessToken, TokenType -> kind) ++
        expiresIn.map (ExpiresIn -> (_:Int).toString) ++
        refreshToken.map (RefreshToken -> _) ++
        (scope match {
          case Seq() => None
          case xs => Some(spaceEncoder(xs))
        }).map (Scope -> _) ++ extras)

  protected def errorResponder(error: String, desc: String, euri: Option[String], state: Option[String]) =
    BadRequest ~> CacheControl("no-store") ~> Pragma("so-cache") ~> Json(Map(Error -> error, ErrorDescription -> desc) ++
        euri.map (ErrorURI -> (_: String)) ++
        state.map (State -> _))

  private def spaceDecoder(raw: String) = raw.replace("""\s+"""," ").split(" ").toSeq

  private def spaceEncoder(scopes: Seq[String]) = scopes.mkString("+")

  def intent = {

    case req @ ContextPath(_, AuthorizePath) & Params(params) =>
      val expected = for {
        responseType <- lookup(ResponseType) is required(requiredMsg(ResponseType)) is watch(_.map(spaceDecoder), e => "")
        clientId     <- lookup(ClientId) is required(requiredMsg(ClientId))
        redirectURI  <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        scope        <- lookup(Scope) is watch(_.map(spaceDecoder), e => "")
        state        <- lookup(State) is optional[String, String]
      } yield {

         (redirectURI.get, responseType.get) match {

           // authorization code flow
           case (ruri, rtx) if(rtx contains(Code)) =>
             auth(AuthorizationCodeRequest(req, rtx, clientId.get, ruri, scope.getOrElse(Nil), state.get)) match {

               case ServiceResponse(resp) => resp

               case AuthorizationCodeResponse(code, state) =>
                  Redirect(ruri ? "code=%s%s".format(
                    code, state.map("&state=%s".format(_)).getOrElse(""))
                  )

               case ErrorResponse(error, desc, euri, state) =>
                 Redirect(ruri ? qstr(
                   Map(Error -> error, ErrorDescription -> desc) ++
                   euri.map(ErrorURI -> (_:String)) ++
                   state.map(State -> _)
                 ))

               case _ => BadRequest
             }

           // implicit token flow
           case (ruri, rtx) if(rtx contains(TokenKey)) =>
             auth(ImplicitAuthorizationRequest(req, rtx, clientId.get, ruri, scope.getOrElse(Nil), state.get)) match {
               case ServiceResponse(cr) => cr
               case ImplicitAccessTokenResponse(accessToken, tokenType, expiresIn, scope, state, extras) =>
                 val frag = qstr(
                   Map(AccessTokenKey -> accessToken, TokenType -> tokenType) ++
                     expiresIn.map(ExpiresIn -> (_:Int).toString) ++
                     (scope match {
                       case Seq() => None
                       case xs => Some(spaceEncoder(xs))
                     }).map(Scope -> _) ++
                     state.map(State -> _ ) ++ extras
                   )
                 Redirect("%s#%s" format(ruri, frag))
               case ErrorResponse(error, desc, euri, state) =>
                 Redirect("%s#%s" format(ruri, qstr(
                   Map(Error -> error, ErrorDescription -> desc) ++
                   euri.map(ErrorURI -> (_:String)) ++
                   state.map(State -> _)
                 )))
               case _ => BadRequest
             }

           // unsupported grant type
           case (ruri, unsupported) =>
             auth(IndeterminateAuthorizationRequest(req, unsupported, clientId.get, ruri, scope.getOrElse(Nil), state.get)) match {
               case ServiceResponse(cr) => cr
               case ErrorResponse(error, desc, euri, state) =>
                 Redirect(ruri ? qstr(
                   Map(Error -> error, ErrorDescription -> desc) ++
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
              Map(Error -> InvalidRequest, ErrorDescription ->  errs.map { _.error }.mkString(", "))
            )
            params(ResponseType) match {
              case Seq(TokenKey) =>
                 Redirect("%s#%s" format(
                   uri, qs
                 ))
              case _ => Redirect(uri ? qs)
            }
          case _ => auth.mismatchedRedirectUri

        }
      }

    case req @ POST(ContextPath(_, TokenPath)) & Params(params) =>
      val expected = for {
        grantType     <- lookup(GrantType) is required(requiredMsg(GrantType))
        code          <- lookup(Code) is optional[String, String]
        clientId      <- lookup(ClientId) is required(requiredMsg(ClientId))
        redirectURI   <- lookup(RedirectURI) is optional[String, String]
        // clientSecret is not recommended to be passed as a parameter by instead
        // encoded in a basic auth header http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-3.1
        clientSecret  <- lookup(ClientSecret) is required(requiredMsg(ClientSecret))
        refreshToken  <- lookup(RefreshToken) is optional[String, String]
        scope         <- lookup(Scope) is watch(_.map(spaceDecoder), e => "")
        userName      <- lookup(Username) is optional[String, String]
        password      <- lookup(Password) is optional[String, String]
      } yield {

        grantType.get match {

          case ClientCredentials =>
            auth(ClientCredentialsRequest(clientId.get, clientSecret.get, scope.getOrElse(Nil))) match {
              case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, _, extras) =>
                accessResponder(
                  accessToken, kind, expiresIn, refreshToken, scope, extras
                )
              case ErrorResponse(error, desc, euri, state) =>
                errorResponder(error, desc, euri, state)
            }

          case Password =>
            (userName.get, password.get) match {
              case (Some(u), Some(pw)) =>
                auth(PasswordRequest(u, pw, clientId.get, clientSecret.get, scope.getOrElse(Nil))) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, _, extras) =>
                    accessResponder(
                      accessToken, kind, expiresIn, refreshToken, scope, extras
                    )
                  case ErrorResponse(error, desc, euri, state) =>
                    errorResponder(error, desc, euri, state)
                }
              case _ =>
                errorResponder(
                  InvalidRequest,
                  (requiredMsg(Username) :: requiredMsg(Password) :: Nil).mkString(" and "),
                  auth.errUri(InvalidRequest), None
                )
            }

          case RefreshToken =>
            refreshToken.get match {
              case Some(rtoken) =>
                auth(RefreshTokenRequest(rtoken, clientId.get, clientSecret.get, scope.getOrElse(Nil))) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, _, extras) =>
                     accessResponder(
                       accessToken, kind, expiresIn, refreshToken, scope, extras
                     )
                  case ErrorResponse(error, desc, euri, state) =>
                    errorResponder(error, desc, euri, state)
                }
              case _ => errorResponder(InvalidRequest, requiredMsg(RefreshToken), None, None)
            }

          case AuthorizationCode =>
            (code.get, redirectURI.get) match {
              case (Some(c), Some(r)) =>
                auth(AccessTokenRequest(c, r, clientId.get, clientSecret.get)) match {
                  case AccessTokenResponse(accessToken, kind, expiresIn, refreshToken, scope, _, extras) =>
                    accessResponder(
                      accessToken, kind, expiresIn, refreshToken, scope, extras
                    )
                  case ErrorResponse(error, desc, euri, state) =>
                    errorResponder(error, desc, euri, state)
                }
              case _ =>
                errorResponder(
                  InvalidRequest,
                  (requiredMsg(Code) :: requiredMsg(RedirectURI) :: Nil).mkString(" and "),
                  auth.errUri(InvalidRequest), None
                )
            }
          case unsupported =>
            // note the oauth2 spec does allow for extention grant types, this implementation currently does not
            errorResponder(UnsupportedGrantType, "%s is unsupported" format unsupported, auth.errUri(UnsupportedGrantType), None)
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
