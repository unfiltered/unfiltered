package unfiltered.netty

import io.netty.buffer.Unpooled
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandler }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{ DefaultFullHttpResponse, HttpVersion, HttpResponseStatus }
import scala.util.control.NonFatal

// note(doug): this type is a little dubious as there as exceptions passed around are no longer wrapped in events. we may wish to remove this
@Sharable
trait ExceptionHandler { self: ChannelInboundHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable): Unit
  override def exceptionCaught(ctx: ChannelHandlerContext,
                               t: Throwable): Unit = {
    onException(ctx, t)
  }
}

/** A ChannelInboundHandler mixin that writes a 500 response to clients before closing the channel
 *  when an exception is thrown */
@Sharable
trait ServerErrorResponse extends ExceptionHandler { self: ChannelInboundHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) = {
    val ch = ctx.channel
    if (ch.isOpen) try {
      System.err.println("Exception caught handling request:")
      t.printStackTrace()
      val res = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR, Unpooled.copiedBuffer(
        "Internal Server Error".getBytes("utf-8")))
      ch.writeAndFlush(res).addListener(ChannelFutureListener.CLOSE)
    } catch {
      case NonFatal(_) => ch.close()
    }
  }
}
