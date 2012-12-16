package unfiltered.oauth2

object OAuth2 {
  val XAuthorizedIdentity = "X-Authorized-Identity"
  val XAuthorizedClientIdentity = "X-Authorized-Client-Identity"
  val XAuthorizedScopes = "X-Authorized-Scopes"
}

/** Extractor for a resource owner and the client they authorized, as well as the granted scope. */
object OAuthIdentity {
  import OAuth2._
  import javax.servlet.http.HttpServletRequest
  import unfiltered.request.HttpRequest

  // todo: how can we accomplish this and not tie ourselves to underlying request?
  /**
   * @return a 3-tuple of (resource-owner-id, client-id, scopes) as an Option, or None if any of these is not available
   * in the request
   */
  def unapply[T <: HttpServletRequest](r: HttpRequest[T]): Option[(String, String, Seq[String])] =
    r.underlying.getAttribute(XAuthorizedIdentity) match {
      case null => None
      case id: String => r.underlying.getAttribute(XAuthorizedClientIdentity) match {
        case null => None
        case clientId: String => r.underlying.getAttribute(XAuthorizedScopes) match {
          case null => Some((id, clientId, Nil))
          case scopes: Seq[String] => Some((id, clientId, scopes))
        }
      }
    }
}
