package unfiltered.request

object HTTPS {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.protocol.equalsIgnoreCase("HTTPS")) Some(req)
    else None
}
