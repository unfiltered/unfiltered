package unfiltered.netty.async

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{
  HttpContent,
  FullHttpRequest,
  HttpRequest  => NettyHttpRequest,
  HttpResponse => NettyHttpResponse }

import unfiltered.Async
import unfiltered.netty.{ ExceptionHandler, ReceivedMessage, RequestBinding, ServerErrorResponse }
import unfiltered.request.HttpRequest
import unfiltered.response._

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
@Sharable // this indicates that the handler is stateless and be called without syncronization
trait Plan extends ChannelInboundHandlerAdapter with ExceptionHandler {
  def intent: Plan.Intent
  private lazy val guardedIntent =
    intent.onPass(
      { req: HttpRequest[ReceivedMessage] =>
        req.underlying.context.fireChannelRead(req.underlying.message) }
    )

  final override def channelReadComplete(ctx: ChannelHandlerContext) =
    ctx.flush()

  override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case req: NettyHttpRequest => guardedIntent {
        new RequestBinding(ReceivedMessage(req, ctx, msg))
      }
      case chunk: HttpContent => ctx.fireChannelRead(chunk)
      case ue => sys.error("Unexpected message type from upstream: %s"
                           .format(ue))
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
