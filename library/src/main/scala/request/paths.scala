package unfiltered.request

object Path {
  def unapply(req: HttpServletRequest) = Some((req.getRequestURI.substring(req.getContextPath.length), req))
}
object Seg {
  def unapply(path: String): Option[List[String]] = path.split("/").toList match {
    case "" :: rest => Some(rest) // skip a leading slash
    case all => Some(all)
  }
}