package unfiltered.netty.async

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{
  HttpContent,
  HttpRequest  => NettyHttpRequest,
  HttpResponse => NettyHttpResponse }
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import unfiltered.Async
import unfiltered.netty.{ ExceptionHandler, ReceivedMessage, RequestBinding, ServerErrorResponse }
import unfiltered.request.HttpRequest
import unfiltered.response._ // for intent.onPass(...) lift

object Plan {
  /** Note: The only return object a channel plan acts on is Pass */
  type Intent = Async.Intent[ReceivedMessage, NettyHttpResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/** A Netty Plan for request-only handling. */
@Sharable
trait Plan extends RequestPlan {
  def intent: Plan.Intent
  def requestIntent = intent
}

/** Common base for async.Plan and future.Plan */
trait RequestPlan extends ChannelInboundHandlerAdapter with ExceptionHandler {
  def requestIntent: Plan.Intent
  private lazy val guardedIntent =
    requestIntent.onPass(
      { (req: HttpRequest[ReceivedMessage]) =>
        req.underlying.context.fireChannelRead(req.underlying.message) }
    )

  final override def channelReadComplete(ctx: ChannelHandlerContext) =
    ctx.flush()

  override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case req: NettyHttpRequest => guardedIntent {
        new RequestBinding(ReceivedMessage(req, ctx, msg))
      }
      // fixme(doug): I don't think this will ever be the case as we are now always adding the aggregator to the pipeline
      case chunk: HttpContent => ctx.fireChannelRead(chunk)
      case frame: WebSocketFrame => ctx.fireChannelRead(frame)
      // fixme(doug): Should we define an explicit exception to catch for this
      case ue => sys.error(s"Unexpected message type from upstream: ${ue}")
    }
}

object Planify {
  @Sharable
  class Planned(intentIn: Plan.Intent) extends Plan
    with ServerErrorResponse {
    val intent = intentIn
  }
  def apply(intentIn: Plan.Intent) = new Planned(intentIn)
}
