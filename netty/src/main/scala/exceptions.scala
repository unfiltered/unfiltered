package unfiltered.netty

import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext, ChannelInboundHandler }
import io.netty.handler.codec.http._
import io.netty.buffer.Unpooled

import unfiltered.util.control.NonFatal

trait ExceptionHandler { self: ChannelInboundHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable)
  override def exceptionCaught(ctx: ChannelHandlerContext,
                               t: Throwable) {    
    onException(ctx, t)
  }
}

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
