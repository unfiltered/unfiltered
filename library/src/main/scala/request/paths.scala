package unfiltered.request

object Path {
  def unapply[T](req: HttpRequest[T]): Some[String] = Some(req.uri.split('?')(0))
  def apply[T](req: HttpRequest[T]): String = req.uri.split('?')(0)
}

object QueryString {
  def unapply[T](req: HttpRequest[T]): Option[String] = req.uri.split('?') match {
    case Array(path) => None
    case Array(path, query) => Some(query)
  }
}

object Seg {
  def unapply(path: String): Some[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}
