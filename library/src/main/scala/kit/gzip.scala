package unfiltered.kit

import unfiltered.request._
import unfiltered.response._
import unfiltered.Cycle

object GZip extends Prepend {

  /** Inserts ResponseFilter.GZip and GZip header into the output
   *  stream if this encoding seems to be supported by the client.
   */
  def intent: Cycle.Intent[Any, Any] = Cycle.Intent[Any, Any] { case Decodes.GZip(req) =>
    ContentEncoding.GZip ~> ResponseFilter.GZip
  }

  /**
   * Wraps request in a RequestFilter.GZip if a Content-Encoding
   * is present for gzip, to handle gzip-encoded requests.
   */
  object Requests extends RequestWrapper {
    def wrap[A]: PartialFunction[HttpRequest[A], HttpRequest[A]] = { case RequestContentEncoding.GZip(req) =>
      RequestFilter.GZip(req)
    }
  }
}
