package unfiltered.oauth2

object OAuth2 {
  val XAuthorizedIdentity = "X-Authorized-Identity"
  val XAuthorizedScopes = "X-Authorized-Scopes"
}

/** resource owner extractor */
object OAuthResourceOwner {
  import OAuth2._
  import javax.servlet.http.HttpServletRequest
  import unfiltered.request.HttpRequest

  def unapply[T <: HttpServletRequest](r: HttpRequest[T]): Option[(String, Option[String])] =
    r.underlying.getAttribute(XAuthorizedIdentity) match {
      case null => None
      case id: String => r.underlying.getAttribute(XAuthorizedScopes) match {
         case null => Some((id, None))
         case scopes: String => Some((id, Some(scopes)))
      }
    }
}
