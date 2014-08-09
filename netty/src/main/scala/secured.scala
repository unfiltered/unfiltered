package unfiltered.netty

import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.ssl.{ NotSslRecordException, SslHandshakeCompletionEvent }

/** Adds ssl handshaking to a channel handler's #channelConnected method
  * This assumes a SslHandler was added to the underlying ChannelPipeline.
  * The ssl handshake will be handled for you. You may wish to know preform custom behavior on
  * if the handshake is a pass or failure. To do so, use the handshakeSuccess(ctx) or handshakeFailure(ctx, event)
  * to do so
  * note(doug): since the handshake of ssl handlers are invoked for you, this traits usefulness is a little dubious. users can just override userEventTriggered, matching on netties SslHandshakeCompletionEvent. we may want to remove this trait altogether as it adds little value */
@deprecated("As a ChannelInboundHandlerAdapter, you may get notified of ssl handshake state overriding userEventTriggered and matching on SslHandshakeCompletionEvents", since="0.8.1")
@Sharable
trait Secured extends ChannelInboundHandlerAdapter with ServerErrorResponse {
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

  def onNotSslRecordException(ctx: ChannelHandlerContext, t: NotSslRecordException): Unit = {}

  /** provides a filter to catch NotSslRecordExceptions raised by SslHandlers, falling back on ServerErrorResponse */
  override def onException(ctx: ChannelHandlerContext, t: Throwable): Unit =
    t match {
      case sslerr: NotSslRecordException  => ()
      case other => super.onException(ctx, t)
    }
}
