package unfiltered.request

object HTTPS {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.getProtocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}
