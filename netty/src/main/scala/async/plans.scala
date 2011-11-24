package unfiltered.netty.async

import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse}
import org.jboss.netty.channel._
import unfiltered.netty._
import unfiltered.response._
import unfiltered.request.HttpRequest
import unfiltered.Async

object Plan {
  /** Note: The only return object a channel plan acts on is Pass */
  type Intent =
    Async.Intent[ReceivedMessage, NHttpResponse]
}
/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/** A Netty Plan for request-only handling. */
trait Plan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  def intent: Plan.Intent
  private lazy val guardedIntent =
    intent.onPass(
      { req: HttpRequest[ReceivedMessage] =>
        req.underlying.context.sendUpstream(req.underlying.event) }
    )
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage() match {
      case req:NHttpRequest => req
      case msg => error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
    guardedIntent(
      new RequestBinding(ReceivedMessage(request, ctx, e))
    )
  }
}

class Planify(val intent: Plan.Intent)
extends Plan with ServerErrorResponse

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
