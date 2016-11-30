package unfiltered.oauth2

import unfiltered.request._
import unfiltered.response._
import unfiltered.request.{ HttpRequest => Req }
import unfiltered.filter.request.ContextPath // work on removing this dep
import unfiltered.directives._, Directives._

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

  // errors

  /**
   * @see [[http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.1.2.1]]
   * @see [[http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-4.2.2.1]]
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
   * @see [[http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3]]
   */
  val AuthorizePath = "/authorize"

  /**
   * @see [[http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3]]
   * @see [[http://tools.ietf.org/html/draft-ietf-oauth-v2-20#section-3.2]]
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

trait Spaces {
  protected def spaceDecoder(raw: String) = raw.replace("""\s+"""," ").split(" ").toSeq

  protected def spaceEncoder(scopes: Seq[String]) = scopes.mkString("+")

}


/** A composition of components which respond to authorization requests.
 *  This trait provides default implementations of Oauth `Flows`. To override these,
 *  simply override a target Flows callback methods */
trait Authorized extends AuthorizationProvider
  with AuthorizationEndpoints with Formatting
  with ValidationMessages with Flows
  with unfiltered.filter.Plan {
  import OAuthorization._

  /** Syntactic sugar for appending query strings to paths */
  implicit class s2qs(uri: String) {
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

  val spaceDecoded = data.Interpreter[Option[String],Option[Seq[String]]](
    _.map(spaceDecoder)
  )

  def onAuthCode(
    req: HttpRequest[Any], responseType: Seq[String], clientId: String,
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

  def onToken(
    req: HttpRequest[Any], 
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

  protected def onUnsupportedAuth(
    req: HttpRequest[Any], responseType: Seq[String], clientId: String,
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

  def intent = Directive.Intent {
    case req @ ContextPath(_, AuthorizePath) =>
      case class BadParam(req: HttpRequest[Any], msg: String)
      extends ResponseJoiner(msg)({ errs =>
        val Params(params) = req
        params(RedirectURI) match {
          case Seq(uri) =>
            val qs = qstr(Map(
              Error -> InvalidRequest,
              ErrorDescription -> errs.mkString(", ")
            ))
            params(ResponseType) match {
              case Seq(TokenKey) =>
                 Redirect("%s#%s" format(
                   uri, qs
                 ))
              case _ => Redirect(uri ? qs)
            }
          case _ => auth.mismatchedRedirectUri(req)
        }
      })

      def required[T](req: HttpRequest[Any]) = data.Requiring[T].fail(name =>
        BadParam(req, requiredMsg(name))
      )

      for {
        responseType <- spaceDecoded ~> required(req) named ResponseType
        clientId     <- required(req) named ClientId
        redirectURI  <- required(req) named RedirectURI
        scope        <- spaceDecoded named Scope
        state        <- data.as.Option[String] named State
      } yield {
        (redirectURI, responseType) match {
          case (ruri, rtx) if(rtx contains(Code)) =>
            onAuthCode(req, rtx, clientId, ruri, scope.getOrElse(Nil), state)
          case (ruri, rtx) if(rtx contains(TokenKey)) =>
            onToken(req, rtx, clientId, ruri, scope.getOrElse(Nil), state)
          case (ruri, unsupported) =>
            onUnsupportedAuth(req, unsupported, clientId, ruri, scope.getOrElse(Nil), state)
        }
      }

    case req @ POST(ContextPath(_, TokenPath)) & Params(params) =>
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
       failure(errorResponder(InvalidRequest, err, None, None))
     }, { mixed =>
        case class BadParam(req: HttpRequest[Any], msg: String)
        extends ResponseJoiner(msg)({ errs =>
          errorResponder(InvalidRequest, errs.mkString(", "), None, None)
        })

        def required[T](req: HttpRequest[Any]) = data.Requiring[T].fail(name =>
          BadParam(req, requiredMsg(name))
        )
        def named(name: String) = mixed.get(name).flatMap(_.headOption)

        def requiredNamed(name: String) =
          data.as.String ~> required(req) named (name, named(name).toSeq)

        def optionNamed(name: String) =
          data.as.String.nonEmpty.named(name, named(name))

        for {
          grantType     <- requiredNamed(GrantType)
          code          <- optionNamed(Code)
          clientId      <- requiredNamed(ClientId)
          redirectURI   <- optionNamed(RedirectURI)
          // clientSecret is not recommended to be passed as a parameter by instead
          // encoded in a basic auth header http://tools.ietf.org/html/draft-ietf-oauth-v2-16#section-3.1
          clientSecret  <- requiredNamed(ClientSecret)
          refreshToken  <- optionNamed(RefreshToken)
          scope         <- spaceDecoded.named(Scope, named(Scope))
          userName      <- optionNamed(Username)
          password      <- optionNamed(Password)
        } yield {

          grantType match {

            case ClientCredentials =>
              onClientCredentials(clientId, clientSecret, scope.getOrElse(Nil))

            case Password =>
              (userName, password) match {
                case (Some(u), Some(pw)) =>
                  onPassword(u, pw, clientId, clientSecret, scope.getOrElse(Nil))
                case _ =>
                  errorResponder(
                    InvalidRequest,
                    (requiredMsg(Username) :: requiredMsg(Password) :: Nil).mkString(" and "),
                    auth.errUri(InvalidRequest), None
                  )
              }

            case RefreshToken =>
              refreshToken match {
                case Some(rtoken) =>
                  onRefresh(rtoken, clientId, clientSecret, scope.getOrElse(Nil))
                case _ => errorResponder(InvalidRequest, requiredMsg(RefreshToken), None, None)
              }

            case AuthorizationCode =>
              (code, redirectURI) match {
                case (Some(c), Some(r)) =>
                  onGrantAuthCode(c, r, clientId, clientSecret)
                case _ =>
                  errorResponder(
                    InvalidRequest,
                    (requiredMsg(Code) :: requiredMsg(RedirectURI) :: Nil).mkString(" and "),
                    auth.errUri(InvalidRequest), None
                  )
              }
            case unsupported =>
              // note the oauth2 spec does allow for extension grant types,
              // this implementation currently does not
              errorResponder(
                UnsupportedGrantType, "%s is unsupported" format unsupported,
                auth.errUri(UnsupportedGrantType), None)
          }
        }
     })
  }
}
