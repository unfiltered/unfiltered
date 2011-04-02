package unfiltered.oauth2

import unfiltered.request.{HttpRequest => Req}

trait ResourceServerProvider { val rsrcSvr: ResourceServer }

trait ResourceServer {
  self: TokenStore =>
  import OAuth2._

  /** User-defined function that returns true if the provided scope is valid for
   *  given resource owner request */
  def validScope[T](owner: String, scopes: Option[String], r: Req[T]): Boolean = true

  def apply[T](r: Req[T], token: String, scopes: Option[String]): OAuthResponse =
    accessToken(token) match {
      case Some(at) =>
        if(!validScope(at.owner, scopes, r)) ErrorResponse(
          InvalidScope, "invalid scope", None, None
        )
        else {
          AuthorizedPass(at.owner, at.scopes)
        }
      case _ => ErrorResponse(InvalidRequest, "invalid token", None, None)
    }
}
