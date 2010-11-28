package unfiltered.request

object RemoteAddr {
  /** @see http://en.wikipedia.org/wiki/Private_network#Private_IPv4_address_spaces 
    * includes private trusted addresses
    * 127.0.0.1 (localhost)
    * private IP 10.x.x.x
    * private IP in the range 172.16.0.0 .. 172.31.255.255
    * private IP 192.168.x.x
    */
  val TrustedProxies = """(^127\.0\.0\.1$|^(10|172\.(1[6-9]|2[0-9]|3[0-1])|192\.168)\.\S+)""".r
  def unapply[T](req: HttpRequest[T]) = Some(req match {
    case XForwardedFor(forwarded) =>
      forwarded.filter(!TrustedProxies.findFirstMatchIn(_).isDefined) match {
        case addr :: _ => addr
        case _ => req.remoteAddr
      }
    case _ => req.remoteAddr
  })
}
