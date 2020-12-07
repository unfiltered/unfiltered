package unfiltered.filter.request

import unfiltered.request.HttpRequest
import jakarta.servlet.http.HttpServletRequest

object ContextPath {
  def unapply[T <: HttpServletRequest](req: HttpRequest[T]): Some[(String, String)] =
    req.underlying.getContextPath() match {
      case ctx => Some((ctx, req.uri.substring(ctx.length).split('?')(0)))
    }
}
