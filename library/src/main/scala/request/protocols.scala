package unfiltered.request

object HTTPS {
  def unapply(req: HttpRequest) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}