package unfiltered.request

/** Basic HTTP Authentication extractor */
object BasicAuth {
  import org.apache.commons.codec.binary.Base64.{decodeBase64}
  import unfiltered.request.{HttpRequest => Req}

  /** @return Some(user, pass) or None */
  def unapply[T](r: Req[T]) = r match {
    case Authorization(auth) => {
      val tok = new java.util.StringTokenizer(auth)
      tok.nextToken match {
        case "Basic" =>
          new String(decodeBase64(tok.nextToken getBytes("utf8"))) split(":") match {
            case Array(u, p) => Some(u, p)
            case _ => None
          }
        case _ => None
      }
    }
    case _ => None
  }
  def apply[T](r: Req[T]) = unapply(r)
}
