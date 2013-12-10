package unfiltered.netty

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.handler.ssl.{ SslHandler, SslHandshakeCompletionEvent }
import io.netty.channel.ChannelHandler.Sharable

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline */
@Sharable // this indicates that the handler is stateless and be called without syncronization
trait Secured extends ChannelInboundHandlerAdapter {
  /** Client code should always pass userEventTriggered events upstream */
  override def userEventTriggered(ctx: ChannelHandlerContext, event: java.lang.Object): Unit =
    event match {
      case complete: SslHandshakeCompletionEvent =>
        channelSecured(ctx, complete)
      case evt =>
        ctx.fireUserEventTriggered(evt)
    }

  /** Called after a successful Ssl handshake. By default, this does nothing but pass the event upstream
    * Override this for post-handshake behavior. */
  def channelSecured(ctx: ChannelHandlerContext, complete: SslHandshakeCompletionEvent): Unit =
    ctx.fireUserEventTriggered(complete)
}
