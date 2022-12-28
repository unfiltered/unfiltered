package unfiltered.netty

import io.netty.channel.{
  ChannelFutureListener,
  ChannelHandlerContext,
  ChannelInboundHandlerAdapter
}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.util.ReferenceCountUtil
import io.netty.handler.codec.http.{
  DefaultHttpResponse, HttpContent, HttpMessage, HttpResponseStatus
}

@Sharable
class NotFoundHandler
  extends ChannelInboundHandlerAdapter {
  override def channelRead(
    ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    (msg match {
      case req: HttpMessage =>
        ReferenceCountUtil.release(req)
        Some(req.protocolVersion)
        // fixme(doug): this may no be unnecessary
      case chunk: HttpContent =>
        ReferenceCountUtil.release(chunk)
        None
      case ue => sys.error(s"Unexpected message type from upstream: ${ue}")
    }).map { version =>
      ctx.channel.writeAndFlush(new DefaultHttpResponse(version, HttpResponseStatus.NOT_FOUND))
         .addListener(ChannelFutureListener.CLOSE)
    }.getOrElse(ctx.fireChannelRead(msg))
}
