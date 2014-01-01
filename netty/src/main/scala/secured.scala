package unfiltered.netty

import io.netty.channel.{ Channel, ChannelFuture, ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.ssl.{ SslHandler, SslHandshakeCompletionEvent }
import io.netty.util.concurrent.{ Future, GenericFutureListener }

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline.
  * The ssl handshake will be handled for you. You may wish to know preform custom behavior on
  * if the handshake is a pass or failure. To do so, use the handshakeSuccess(ctx) or handshakeFailure(ctx, event)
  * to do so
  * note(doug): since the handshake of ssl handlers are not invoked for you. this traits usefulness is a little dubious. users can just override userEventTriggered, matching on netties SslHandshakeCompletionEvent. we may want to remove this trait altogether as it adds little value */
@Sharable
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
  def handshakeSuccess(ctx: ChannelHandlerContext): Unit = println("handshake success")

  /** Called after a failed ssl handshake attempt */
  def handshakeFailure(ctx: ChannelHandlerContext, event: SslHandshakeCompletionEvent): Unit = println("handshake failure")

}
