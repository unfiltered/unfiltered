package unfiltered.netty

import io.netty.buffer.Unpooled
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext }
import io.netty.handler.codec.http.{ DefaultFullHttpResponse, FullHttpRequest, HttpUtil }
import io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN
import io.netty.handler.codec.http.HttpVersion.HTTP_1_1
import io.netty.util.{ CharsetUtil, ReferenceCountUtil }

/** Module defining function types used in the WebSockets module as well as
 *  function defaults */
package object websockets {

  /** The transition from an http request handling to websocket request handling.
   *  Note: This can not be an Async.Intent because RequestBinding is a Responder for HttpResponses */
  type Intent = PartialFunction[RequestBinding, SocketIntent]

  /** A SocketIntent is the result of a handler `lift`ing a request into
   *  the WebSocket protocol. WebSockets may be responded to asynchronously,
   * thus their handler will not need to return a value */
  type SocketIntent = PartialFunction[SocketCallback, Unit]

  /** A pass handler type represents a means to forward a request upstream for
   *  unhandled patterns and protocol messages */
  type PassHandler = (ChannelHandlerContext, java.lang.Object) => Unit

  /** Equivalent of an HttpResponse's Pass function,
   *  a SocketIntent that does nothing */
  val Pass: SocketIntent  = { case _ => () }

  val PassAlong: Intent = { case _ => Pass }

  /** A default implementation of a Plan.PassHandler which returns a HTTP protocol
   *  forbidden response code to the channel before closing the channel */
  val DefaultPassHandler: PassHandler = { (ctx, message) =>
    message match {
      case req: FullHttpRequest =>
        val res = new DefaultFullHttpResponse(
          HTTP_1_1, FORBIDDEN, Unpooled.copiedBuffer(
            FORBIDDEN.toString, CharsetUtil.UTF_8))
        HttpUtil.setContentLength(res, res.content.readableBytes)
        ReferenceCountUtil.release(req)
        ctx.channel.writeAndFlush(res).addListener(
          ChannelFutureListener.CLOSE)
      case invalid =>
        sys.error(s"Invalid type of event message (${invalid.getClass.getName}) for Plan pass handling")
    }
  }
}
