package unfiltered

/** Module defining Cookie-related constants
 *  See also http://tools.ietf.org/html/rfc2965#page-5 */
object CookieKeys {
  val Path = "Path"
  val Expires = "Expires"
  val MaxAge = "Max-Age"
  val Domain = "Domain"
  val Secure = "Secure"
  val HTTPOnly = "HTTPOnly"
  val Comment = "Comment"
  val CommentURL = "CommentURL"
  val Discard = "Discard"
  val Port = "Port"
  val Version = "Version"

  val LCPath = Path.toLowerCase
  val LCExpires = Expires.toLowerCase
  val LCMaxAge = MaxAge.toLowerCase
  val LCDomain = Domain.toLowerCase
  val LCSecure = Secure.toLowerCase
  val LCHTTPOnly = HTTPOnly.toLowerCase
  val LCComment = Comment.toLowerCase
  val LCCommentURL = CommentURL.toLowerCase
  val LCDiscard = Discard.toLowerCase
  val LCPort = Port.toLowerCase
  val LCVersion = Version.toLowerCase

  /** Named properties that do not have an associated value */
  val KeyOnly = Seq(Discard, Secure, HTTPOnly)
}

case class Cookie(name: String, value: String, domain: Option[String] = None,
                  path: Option[String] = None, maxAge: Option[Int] = None,
                  secure: Option[Boolean] = None, httpOnly: Boolean = false,
                  version: Int = 0)
