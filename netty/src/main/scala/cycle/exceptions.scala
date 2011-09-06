package unfiltered.netty.cycle

import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus.INTERNAL_SERVER_ERROR
import org.jboss.netty.handler.codec.http._
import org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1
import org.jboss.netty.buffer.ChannelBuffers

trait InternalServerError { self: Plan =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
    val ch = ctx.getChannel
    if (ch.isOpen) try {
      System.err.println("Exception caught handling request:")
      t.printStackTrace()
      val res = new DefaultHttpResponse(HTTP_1_1, INTERNAL_SERVER_ERROR)
      res.setContent(ChannelBuffers.copiedBuffer(
        "Internal Server Error".getBytes("utf-8")))
        ch.write(res).addListener(ChannelFutureListener.CLOSE)
    } catch {
      case _ => ch.close()
    }
  }
}
