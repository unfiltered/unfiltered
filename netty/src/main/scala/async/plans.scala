package unfiltered.netty.async

import org.jboss.netty.handler.codec.http.{HttpRequest=>NHttpRequest,
                                           HttpResponse=>NHttpResponse,
                                           HttpChunk=>NHttpChunk}
import org.jboss.netty.channel._
import unfiltered.netty._
import unfiltered.response._
import unfiltered.request.HttpRequest
import unfiltered.Async

object Plan {
  /** Note: The only return object an async plan acts on is Pass */
  type Intent =
    Async.Intent[ReceivedMessage, NHttpResponse]
}
/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/** A Netty Plan async interaction, DIY responding */
trait Plan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  def intent: Plan.Intent
  def requestPlan = intent
}

/** Common base for async.Plan and future.Plan */
trait RequestPlan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  def requestIntent: Plan.Intent
  private lazy val guardedIntent =
    requestIntent.onPass(
      { req: HttpRequest[ReceivedMessage] =>
        req.underlying.context.sendUpstream(req.underlying.event) }
    )
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage() match {
      case req:NHttpRequest => guardedIntent {
        new RequestBinding(ReceivedMessage(req, ctx, e))
      }
      case chunk:NHttpChunk => ctx.sendUpstream(e)
      case msg => sys.error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
  }
}

object Planify {
  def apply(intentIn: Plan.Intent) = new Plan with ServerErrorResponse {
    val intent = intentIn
  }
}
