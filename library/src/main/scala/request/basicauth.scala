package unfiltered.request.auth

import unfiltered.request.Authorization

/** Basic HTTP Authentication extractor */
object BasicAuth {
  import org.apache.commons.codec.binary.Base64.{decodeBase64}
  import javax.servlet.http.{HttpServletRequest => Req}
  
  /** @return Some(((user, pass), req)) or None */
  def unapply(r: Req) = r match {
    case Authorization(auth) => {
      val tok = new java.util.StringTokenizer(auth)
      tok.nextToken match {
        case "Basic" =>
          new String(decodeBase64(tok.nextToken getBytes("utf8"))) split(":") match {
            case Array(u, p) => Some(((u, p), r))
            case _ => None
          }
        case _ => None
      }
    }
    case _ => None
  }
}