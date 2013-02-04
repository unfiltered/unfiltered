package unfiltered.netty

/** Module defining function types used in the WebSockets module as well as
 *  function defaults */
package object websockets {
  import org.jboss.{ netty => jnetty }
  import jnetty.buffer.ChannelBuffers
  import jnetty.channel.{ ChannelEvent, ChannelFutureListener, ChannelHandlerContext, MessageEvent }
  import jnetty.handler.codec.http.{ HttpRequest => NHttpRequest, DefaultHttpResponse }
  import jnetty.handler.codec.http.HttpHeaders
  import jnetty.handler.codec.http.HttpResponseStatus.FORBIDDEN
  import jnetty.handler.codec.http.HttpVersion.HTTP_1_1
  import jnetty.util.CharsetUtil
  /** The transition from an http request handling to websocket request handling.
   *  Note: This can not be an Async.Intent because RequestBinding is a Responder for HttpResponses */
  type Intent = PartialFunction[RequestBinding, SocketIntent]

  /** A SocketIntent is the result of a handler `lift`ing a request into
   *  the WebSocket protocol. WebSockets may be responded to asynchronously,
   * thus their handler will not need to return a value */
  type SocketIntent = PartialFunction[SocketCallback, Unit]

  /** A pass handler type represents a means to forward a request upstream for
   *  unhandled patterns and protocol messages */
  type PassHandler = (ChannelHandlerContext, ChannelEvent) => Unit

  /** Equivalent of an HttpResponse's Pass function,
   *  a SocketIntent that does nothing */
  val Pass  = ({ case _ => () }: SocketIntent)

  /** A default implementation of a Plan.PassHandler which returns a HTTP protocol
   *  forbidden response code to the channel before closing the channel */
  val DefaultPassHandler = ({ (ctx, event) =>
    event match {
      case e: MessageEvent =>
        e.getMessage match {
          case _: NHttpRequest =>
            val res = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN)
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8))
            HttpHeaders.setContentLength(res, res.getContent.readableBytes)
            ctx.getChannel.write(res).addListener(ChannelFutureListener.CLOSE)
          case msg =>
            sys.error("Invalid type of event message (%s) for Plan pass handling".format(
              msg.getClass.getName))
        }
      case _ => () // we really only care about MessageEvents but need to support the more generic ChannelEvent
    }
   }: PassHandler)
}
