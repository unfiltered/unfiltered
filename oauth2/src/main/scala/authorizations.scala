package unfiltered.oauth2

import unfiltered._
import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{ HttpRequest => Req }
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

/** Configured Authorization server module */
case class OAuthorization(val auth: AuthorizationServer) extends Authorized
     with DefaultAuthorizationPaths with DefaultValidationMessages


/** A composition of components which respond to authorization requests.
 *  This trait provides default implementations of Oauth `Flows`. To override these,
 *  simply override a target Flows callback methods */
trait Authorized extends AuthorizationProvider
  with AuthorizationEndpoints with Formatting
  with ValidationMessages with Flows
  with unfiltered.filter.Plan {
  import QParams._
  import OAuthorization._

  /** Syntactic sugar for appending query strings to paths */
  implicit def s2qs(uri: String) = new {
     def ?(qs: String) =
       "%s%s%s" format(uri, if(uri.indexOf("?") > 0) "&" else "?", qs)
  }

  /** @return a function which builds a
   *  response for an accept token request */
  protected def accessResponder(
    accessToken: String,
    tokenType: Option[String],
    expiresIn: Option[Int],
    refreshToken: Option[String],
    scope:Seq[String],
    extras: Iterable[(String, String)]) =
      CacheControl("no-store") ~> Pragma("no-cache") ~>
        Json(Map(AccessTokenKey -> accessToken) ++
          tokenType.map(TokenType -> _) ++
          expiresIn.map (ExpiresIn -> (_:Int).toString) ++
             refreshToken.map (RefreshToken -> _) ++
             (scope match {
               case Seq() => None
               case xs => Some(spaceEncoder(xs))
             }).map (Scope -> _) ++ extras)

  /** @return a function which builds a
   *  response for error responses */
  protected def errorResponder(
    error: String, desc: String,
    euri: Option[String], state: Option[String]) =
      BadRequest ~> CacheControl("no-store") ~> Pragma("no-cache") ~>
        Json(Map(Error -> error, ErrorDescription -> desc) ++
          euri.map (ErrorURI -> (_: String)) ++
          state.map (State -> _))

  protected def spaceDecoder(raw: String) = raw.replace("""\s+"""," ").split(" ").toSeq

  protected def spaceEncoder(scopes: Seq[String]) = scopes.mkString("+")

  def onAuthCode[T](
    req: HttpRequest[T], responseType: Seq[String], clientId: String,
    redirectUri: String, scope: Seq[String], state: Option[String]) =
      auth(AuthorizationCodeRequest(
        req, responseType, clientId, redirectUri, scope, state)) match {

          case ServiceResponse(resp) => resp

          case AuthorizationCodeResponse(code, state) =>
            Redirect(redirectUri ? "code=%s%s".format(
              code, state.map("&state=%s".format(_)).getOrElse(""))
            )

          case ErrorResponse(error, desc, euri, state) =>
            Redirect(redirectUri ? qstr(
              Map(Error -> error, ErrorDescription -> desc) ++
              euri.map(ErrorURI -> (_:String)) ++
              state.map(State -> _)
            ))

          case _ => BadRequest
        }

  def onToken[T](
    req: HttpRequest[T], 
    responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]) =
    auth(ImplicitAuthorizationRequest(
      req, responseType, clientId, redirectUri, scope, state)) match {
        case ServiceResponse(cr) => cr
        case ImplicitAccessTokenResponse(
          accessToken, tokenType, expiresIn, scope, state, extras) =>
          val fragment = qstr(
            Map(AccessTokenKey -> accessToken) ++
              tokenType.map(TokenType -> _) ++
              expiresIn.map(ExpiresIn -> (_:Int).toString) ++
              (scope match {
                case Seq() => None
                case xs => Some(spaceEncoder(xs))
              }).map(Scope -> _) ++
              state.map(State -> _ ) ++ extras
            )
          Redirect("%s#%s" format(redirectUri, fragment))
        case ErrorResponse(error, desc, euri, state) =>
          Redirect("%s#%s" format(redirectUri, qstr(
            Map(Error -> error, ErrorDescription -> desc) ++
              euri.map(ErrorURI -> (_:String)) ++
              state.map(State -> _)
            )))
        case _ => BadRequest
      }

  protected def onUnsupportedAuth[T](
    req: HttpRequest[T], responseType: Seq[String], clientId: String,
    redirectUri: String, scope: Seq[String], state: Option[String]) =
    auth(IndeterminateAuthorizationRequest(
      req, responseType, clientId, redirectUri, scope, state)) match {
      case ServiceResponse(cr) => cr
      case ErrorResponse(error, desc, euri, state) =>
        Redirect(redirectUri ? qstr(
          Map(Error -> error, ErrorDescription -> desc) ++
            euri.map(ErrorURI -> (_:String)) ++
            state.map(State -> _)
          ))
      case _ => BadRequest
    }

  def onClientCredentials(clientId: String, clientSecret: String, scope: Seq[String]) =
    auth(ClientCredentialsRequest(
      clientId, clientSecret, scope)) match {
        case AccessTokenResponse(
          accessToken, tokenType, expiresIn, refreshToken, scope, _, extras) =>
            accessResponder(
              accessToken, tokenType, expiresIn, refreshToken, scope, extras
            )
        case ErrorResponse(error, desc, euri, state) =>
          errorResponder(error, desc, euri, state)
      }

  def onPassword(
    userName: String, password: String,
    clientId: String, clientSecret: String, scope: Seq[String]) =
    auth(PasswordRequest(
      userName, password, clientId, clientSecret, scope)) match {
        case AccessTokenResponse(
          accessToken, tokenType, expiresIn, refreshToken, scope, _, extras) =>
            accessResponder(
              accessToken, tokenType, expiresIn, refreshToken, scope, extras
            )
        case ErrorResponse(error, desc, euri, state) =>
          errorResponder(error, desc, euri, state)
      }

  def onGrantAuthCode(code: String, redirectUri: String, clientId: String, clientSecret: String) =
     auth(AccessTokenRequest(code, redirectUri, clientId, clientSecret)) match {
       case AccessTokenResponse(
         accessToken, tokenType, expiresIn, refreshToken, scope, _, extras) =>
           accessResponder(
             accessToken, tokenType, expiresIn, refreshToken, scope, extras
           )
       case ErrorResponse(error, desc, euri, state) =>
         errorResponder(error, desc, euri, state)
     }

  def onRefresh(refreshToken: String, clientId: String, clientSecret: String, scope: Seq[String]) =
    auth(RefreshTokenRequest(
      refreshToken, clientId, clientSecret, scope)) match {
        case AccessTokenResponse(
          accessToken, tokenType, expiresIn, refreshToken, scope, _, extras) =>
            accessResponder(
              accessToken, tokenType, expiresIn, refreshToken, scope, extras
            )
        case ErrorResponse(error, desc, euri, state) =>
          errorResponder(error, desc, euri, state)
      }

  def intent = {
    case req @ ContextPath(_, AuthorizePath) & Params(params) =>
      val expected = for {
        responseType <- lookup(ResponseType) is required(requiredMsg(ResponseType)) is
                          watch(_.map(spaceDecoder), e => "")
        clientId     <- lookup(ClientId) is required(requiredMsg(ClientId))
        redirectURI  <- lookup(RedirectURI) is required(requiredMsg(RedirectURI))
        scope        <- lookup(Scope) is watch(_.map(spaceDecoder), e => "")
        state        <- lookup(State) is optional[String, String]
      } yield {
        (redirectURI.get, responseType.get) match {
          case (ruri, rtx) if(rtx contains(Code)) =>
            onAuthCode(req, rtx, clientId.get, ruri, scope.getOrElse(Nil), state.get)
          case (ruri, rtx) if(rtx contains(TokenKey)) =>
            onToken(req, rtx, clientId.get, ruri, scope.getOrElse(Nil), state.get)
          case (ruri, unsupported) =>
            onUnsupportedAuth(req, unsupported, clientId.get, ruri, scope.getOrElse(Nil), state.get)
        }
      }

      expected(params) orFail { errs =>
        params(RedirectURI) match {
          case Seq(uri) =>
            val qs = qstr(Map(
              Error -> InvalidRequest,
              ErrorDescription -> errs.map { _.error }.mkString(", ")
            ))
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
            onClientCredentials(clientId.get, clientSecret.get, scope.getOrElse(Nil))

          case Password =>
            (userName.get, password.get) match {
              case (Some(u), Some(pw)) =>
                onPassword(u, pw, clientId.get, clientSecret.get, scope.getOrElse(Nil))
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
                onRefresh(rtoken, clientId.get, clientSecret.get, scope.getOrElse(Nil))
              case _ => errorResponder(InvalidRequest, requiredMsg(RefreshToken), None, None)
            }

          case AuthorizationCode =>
            (code.get, redirectURI.get) match {
              case (Some(c), Some(r)) =>
                onGrantAuthCode(c, r, clientId.get, clientSecret.get)
              case _ =>
                errorResponder(
                  InvalidRequest,
                  (requiredMsg(Code) :: requiredMsg(RedirectURI) :: Nil).mkString(" and "),
                  auth.errUri(InvalidRequest), None
                )
            }
          case unsupported =>
            // note the oauth2 spec does allow for extention grant types,
            // this implementation currently does not
            errorResponder(
              UnsupportedGrantType, "%s is unsupported" format unsupported,
              auth.errUri(UnsupportedGrantType), None)
        }
      }

    // here, we are combining requests parameters with basic authentication headers
    // the preferred way of providing client credentials is through 
    // basic auth but this is not required. The following folds basic auth data
    // into the params ensuring there is no conflict in transports
     val combinedParams = (
       (Right(params): Either[String, Map[String, Seq[String]]]) /: BasicAuth(req)
     )((a,e) => e match {
       case (clientId, clientSecret) =>
         val preferred = Right(
           a.right.get ++ Map(ClientId -> Seq(clientId), ClientSecret-> Seq(clientSecret))
         )
         a.right.get(ClientId) match {
           case Seq(id) =>
             if(id == clientId) preferred else Left("client ids did not match")
           case _ => preferred
         }
       case _ => a
     })

     combinedParams fold({ err =>
       errorResponder(InvalidRequest, err, None, None)
     }, { mixed =>
       expected(mixed) orFail { errs =>
         errorResponder(InvalidRequest, errs.map { _.error }.mkString(", "), None, None)
       }
     })
  }
}
