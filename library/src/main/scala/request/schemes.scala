package unfiltered.request

object HTTPS {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.isSecure) Some(req)
    else None
}

object HTTP {
  def unapply[T](req: HttpRequest[T]) = 
    if (req.isSecure) None
    else Some(req)
}