package unfiltered.netty

import io.netty.channel.{ Channel, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.handler.ssl.{ SslHandler, SslHandshakeCompletionEvent }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.util.concurrent.{ Future, GenericFutureListener }

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline.
  * The ssl handshake will be handled for you. You may wish to know preform custom behavior on
  * if the handshake is a pass or failure. To do so, use the handshakeSuccess(ctx) or handshakeFailure(ctx, event)
  * to do so */
@Sharable // this indicates that the handler is stateless and be called without synchronization
trait Secured extends ChannelInboundHandlerAdapter {
  /** Client code should _always_ pass userEventTriggered events upstream */
  override def userEventTriggered(ctx: ChannelHandlerContext, event: java.lang.Object): Unit =
    event match {
      case complete: SslHandshakeCompletionEvent =>
        if (complete.isSuccess) handshakeSuccess(ctx)
        else handshakeFailure(ctx, complete)
      case evt =>
        ctx.fireUserEventTriggered(evt)
    }

  /** Called after a successful ssl handshake attempt */
  def handshakeSuccess(ctx: ChannelHandlerContext): Unit = {}

  /** Called after a failed ssl handshake attempt */
  def handshakeFailure(ctx: ChannelHandlerContext, event: SslHandshakeCompletionEvent): Unit = {}

}
