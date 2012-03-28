package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

object Secure {
  def redir[A,B](intent: unfiltered.Cycle.Intent[A,B],
                 port: Int = -1) = {
    intent.fold(
      { _ => Pass },
      {
        case (HTTPS(req), rf) => rf
        case (req @ Host(host), rf) =>
          val h = host.split(':')(0)
          Redirect(
            if (port > -1)
              "https://%s:%d%s".format(h, port, req.uri)
            else
              "https://%s%s".format(h, req.uri)
          )
      }
    )
  }
}
