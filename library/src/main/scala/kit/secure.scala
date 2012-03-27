package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

object Secure {
  def redir[A,B](intent: unfiltered.Cycle.Intent[A,B]) = {
    intent.fold(
      { _ => Pass },
      {
        case (HTTPS(req), rf) => rf
        case (req @ Host(host), rf) =>
          Redirect("https://%s%s".format(host, req.uri))
      }
    )
  }
}
