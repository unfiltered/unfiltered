package unfiltered.netty

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.handler.ssl.{ SslHandler, SslHandshakeCompletionEvent }

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline */
trait Secured extends ChannelInboundHandlerAdapter {
  /** Client code should always pass userEventTriggered events upstream */
  override def userEventTriggered(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case complete: SslHandshakeCompletionEvent =>
        channelSecured(ctx, complete)
      case _ => ()
    }

  /** Called after a successful Ssl handshake. By default, this does nothing. 
    * Override this for post-handshake behavior. */
  def channelSecured(ctx: ChannelHandlerContext, complete: SslHandshakeCompletionEvent): Unit = ()
}
