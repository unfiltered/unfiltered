package unfiltered.netty

import io.netty.buffer.Unpooled
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandler }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{ DefaultFullHttpResponse, HttpVersion, HttpResponseStatus }
import unfiltered.util.control.NonFatal

@Sharable
trait ExceptionHandler { self: ChannelInboundHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable)
  override def exceptionCaught(ctx: ChannelHandlerContext,
                               t: Throwable) {    
    onException(ctx, t)
  }
}

@Sharable
trait ServerErrorResponse { self: ChannelInboundHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
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
