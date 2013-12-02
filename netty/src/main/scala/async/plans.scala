
package unfiltered.netty.async

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter } // was SimpleChannelUpstreamHandler
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{
  HttpContent,
  FullHttpRequest,
  HttpRequest  => NHttpRequest,
  HttpResponse => NHttpResponse }

import unfiltered.Async
import unfiltered.netty._
import unfiltered.response._
import unfiltered.request.HttpRequest

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
@Sharable // this indicates that the handler is stateless and be called without syncronization
trait Plan extends ChannelInboundHandlerAdapter with ExceptionHandler {
  def intent: Plan.Intent
  private lazy val guardedIntent =
    intent.onPass(
      { req: HttpRequest[ReceivedMessage] =>
        req.underlying.context.fireChannelRead(req.underlying.message) }
    )

  override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case req: FullHttpRequest => guardedIntent {
        new RequestBinding(ReceivedMessage(req, ctx, msg))
      }
      case chunk: HttpContent => ctx.fireChannelRead(chunk)
      case ue => sys.error("Unexpected message type from upstream: %s"
                           .format(ue))
    }

  /*override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getMessage() match {
      case req: NHttpRequest => guardedIntent {
        new RequestBinding(ReceivedMessage(req, ctx, e))
      }
      case chunk: HttpContent => ctx.sendUpstream(e)
      case msg => sys.error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
  }*/
}

object Planify {
  def apply(intentIn: Plan.Intent) = new Plan with ServerErrorResponse {
    val intent = intentIn
  }
}
