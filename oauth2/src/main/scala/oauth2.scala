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

  // todo: how can we accomplish this and not tie ourselves to underlying request?
  def unapply[T <: HttpServletRequest](r: HttpRequest[T]): Option[(String, Seq[String])] =
    r.underlying.getAttribute(XAuthorizedIdentity) match {
      case null => None
      case id: String => r.underlying.getAttribute(XAuthorizedScopes) match {
         case null => Some((id, Nil))
         case scopes: Seq[String] => Some((id, scopes))
      }
    }
}
