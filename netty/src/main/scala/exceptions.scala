package unfiltered.netty

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.buffer.ChannelBuffers

import unfiltered.util.control.NonFatal

trait ExceptionHandler { self: SimpleChannelUpstreamHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable)
  override def exceptionCaught(ctx: ChannelHandlerContext,
                               e: ExceptionEvent) {
    onException(ctx, e.getCause)
  }
}

trait ServerErrorResponse { self: ExceptionHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
    val ch = ctx.getChannel
    if (ch.isOpen) try {
      System.err.println("Exception caught handling request:")
      t.printStackTrace()
      val res = new DefaultHttpResponse(
        HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
      res.setContent(ChannelBuffers.copiedBuffer(
        "Internal Server Error".getBytes("utf-8")))
        ch.write(res).addListener(ChannelFutureListener.CLOSE)
    } catch {
      case NonFatal(_) => ch.close()
    }
  }
}
