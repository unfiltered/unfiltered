package unfiltered.request

object Path {
  def unapply[T](req: HttpRequest[T]) = Some(req.uri.split('?')(0))
}

object Seg {
  def unapply(path: String): Option[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}
