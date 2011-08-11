package unfiltered.netty.channel

import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction
import unfiltered.netty.ReceivedMessage
import org.jboss.netty.handler.codec.http.{HttpResponse=>NHttpResponse}

/** Convenience function for writing out a response when available */
object Respond {
  def apply(request: HttpRequest[ReceivedMessage]) =
    request.underlying.respond _
}
