package unfiltered.netty

import org.jboss.netty.channel.{ChannelHandlerContext, ChannelFutureListener,
  ChannelFuture, ChannelStateEvent, SimpleChannelUpstreamHandler}

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline */
trait Secured extends SimpleChannelUpstreamHandler {
  import org.jboss.netty.handler.ssl.SslHandler
  
  override def channelConnected(ctx: ChannelHandlerContext,
                                e: ChannelStateEvent) =
    ctx.getPipeline.get(classOf[SslHandler]) match {
      case null => ()
      case ssl: SslHandler => ssl.handshake.addListener(channelSecured(ctx))
    }
  
  /** Called after a successful Ssl handshake. By default, this does nothing. 
    * Override this for post-handshake behavior. */
  def channelSecured(ctx: ChannelHandlerContext) = new ChannelFutureListener {
    def operationComplete(future: ChannelFuture) { /* NO OP */ }
  }
}
