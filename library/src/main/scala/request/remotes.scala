package unfiltered.request

object RemoteAddr {
  // filterNot shim for scala 2.7.7 line
  class FilterNotList[T](l:List[T]) {
    def filterNot(p: T => Boolean) = l.filter(!p(_))
  }
  implicit def l2fnl[T](l: List[T]) = new FilterNotList(l)
  /** @see http://en.wikipedia.org/wiki/Private_network#Private_IPv4_address_spaces */
  val TrustedProxies = """(^127\.0\.0\.1$|^(10|172\.(1[6-9]|2[0-9]|3[0-1])|192\.168)\.\S+)""".r
  def unapply[T](req: HttpRequest[T]) = Some((req match {
    case XForwardedFor(forwarded,  _) =>
      forwarded.filterNot(TrustedProxies.findFirstMatchIn(_).isDefined) match {
        case List(addr, _) => addr
        case _ => req.remoteAddr
      }
    case _ => req.remoteAddr
  }, req))
}