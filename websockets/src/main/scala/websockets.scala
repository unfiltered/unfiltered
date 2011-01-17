package unfiltered.websockets

import org.jboss.{netty => jnetty}

import jnetty.channel.{Channel, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.ChannelBuffer

trait SocketCallback
case class Open(socket: WebSocket) extends SocketCallback
case class Close(socket: WebSocket) extends SocketCallback
case class Message(socket: WebSocket, msg: Msg) extends SocketCallback
case class Error(socket: WebSocket, err: Throwable) extends SocketCallback

sealed trait Msg
case class Text(txt: String) extends Msg
case class Binary(buf: jnetty.buffer.ChannelBuffer) extends Msg

case class WebSocket(channel: Channel) {
  import jnetty.handler.codec.http.websocket.DefaultWebSocketFrame

  def send(str: String) = channel.write(new DefaultWebSocketFrame(str))

  /** will throw an IllegalArgumentException if (type & 0x80 == 0) and the data is not
   * encoded in UTF-8 */
  def send(mtype: Int, buf: ChannelBuffer) = channel.write(new DefaultWebSocketFrame(mtype, buf))
}

object WebSocketHandler {
  import jnetty.channel.ChannelFutureListener
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.{HttpRequest => NHttpRequest, DefaultHttpResponse}
  import jnetty.handler.codec.http.HttpVersion.HTTP_1_1
  import jnetty.handler.codec.http.HttpResponseStatus.FORBIDDEN
  import jnetty.handler.codec.http.HttpHeaders._
  import jnetty.util.CharsetUtil

  type Intent = PartialFunction[SocketCallback, Unit]

  /** Creates a WebSocketHandler that, when passes, will return a forbidden
   *  response to the client */
  def apply(path: String, intent: Intent): WebSocketHandler =
    WebSocketHandler(path, intent, { (ctx, event) =>
      event.getMessage match {
        case request: NHttpRequest =>
          val res = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN)
          res.setContent(ChannelBuffers.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8))
          setContentLength(res, res.getContent.readableBytes)
          ctx.getChannel.write(res).addListener(ChannelFutureListener.CLOSE)
        case msg => error("Invalid pass message type (%s) for WebSocketHandler" format msg.getClass.getName)
      }
    })
}

case class WebSocketHandler(path: String, intent: WebSocketHandler.Intent,
                            pass: (ChannelHandlerContext, MessageEvent) => Unit)
  extends SimpleChannelUpstreamHandler {

  import java.security.MessageDigest
  import jnetty.channel.{ChannelFuture, ChannelFutureListener,
                         ChannelStateEvent, ExceptionEvent}
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.websocket.{DefaultWebSocketFrame, WebSocketFrame,
                                             WebSocketFrameDecoder, WebSocketFrameEncoder}
  import jnetty.handler.codec.http.{HttpHeaders, HttpMethod, HttpRequest => NHttpRequest,
                                   HttpResponseStatus, HttpVersion,  DefaultHttpResponse,
                                   HttpResponse => NHttpResponse}
  import jnetty.handler.codec.http
  import HttpHeaders._
  import HttpHeaders.Names.{CONNECTION, ORIGIN, HOST, UPGRADE, SEC_WEBSOCKET_LOCATION,
                            SEC_WEBSOCKET_ORIGIN, SEC_WEBSOCKET_PROTOCOL,
                            SEC_WEBSOCKET_KEY1, SEC_WEBSOCKET_KEY2}
  import HttpHeaders.Values._

  import jnetty.util.CharsetUtil

  /** 0x00-0x7F typed frame becomes (UTF-8) Text
      0x80-0xFF typed frame becomes Binary */
  implicit def wsf2msg(wsf: WebSocketFrame): Msg =
    if(wsf.isText) Text(wsf.getTextData) else Binary(wsf.getBinaryData)

  def attempt = intent.orElse({ case _ => () }: WebSocketHandler.Intent)

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case http: NHttpRequest => upgrade(ctx, http, event)
      case frame: WebSocketFrame => attempt(Message(WebSocket(ctx.getChannel), frame))
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    event.getChannel.close
  }

  private def upgrade(ctx: ChannelHandlerContext, request: NHttpRequest, event: MessageEvent) =
    request.getMethod match {
      case HttpMethod.GET => request.getUri match {
        case p: String if(p.equals(path)) =>
          if (Values.UPGRADE.equalsIgnoreCase(request.getHeader(CONNECTION)) &&
              Values.WEBSOCKET.equalsIgnoreCase(request.getHeader(UPGRADE))) {

            val response = new DefaultHttpResponse(HttpVersion.HTTP_1_1, new HttpResponseStatus(101, "Web Socket Protocol Handshake"))

            def head(k: String, v: String) = response.addHeader(k, v)
            def headIfDefined(k: String) = if(request.getHeader(k) != null) head(k, request.getHeader(k))

            head(UPGRADE, Values.WEBSOCKET)
            head(CONNECTION, Values.UPGRADE)
            head(SEC_WEBSOCKET_ORIGIN, request.getHeader(ORIGIN))
            head(SEC_WEBSOCKET_LOCATION, location(request))
            headIfDefined(SEC_WEBSOCKET_PROTOCOL)

            if (request.containsHeader(SEC_WEBSOCKET_KEY1) && request.containsHeader(SEC_WEBSOCKET_KEY2)) {
              val buff = ChannelBuffers.buffer(16)
              List(request.getHeader(SEC_WEBSOCKET_KEY1), request.getHeader(SEC_WEBSOCKET_KEY2)).foreach( k =>
                buff.writeInt((k.replaceAll("[^0-9]", "").toLong / k.replaceAll("[^ ]", "").length).toInt)
              )
              buff.writeLong(request.getContent().readLong)
              response.setContent(ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(buff.array)))
            }

            val pipe = ctx.getChannel.getPipeline
            if(pipe.get("aggregator") != null) {
              pipe.remove("aggregator")
            }
            pipe.replace("decoder", "wsdecoder", new WebSocketFrameDecoder)

            ctx.getChannel.write(response)
            ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
              def operationComplete(future: ChannelFuture) =
               attempt(Close(WebSocket(ctx.getChannel)))
            })

            pipe.replace("encoder", "wsencoder", new WebSocketFrameEncoder)

            attempt(Open(WebSocket(ctx.getChannel)))

          } else pass(ctx, event) /* not a handshake request */

        case _ => pass(ctx, event) /* some other path */
      }
      case _ => pass(ctx, event) /* some other http method */
    }

  /** By default, when a websocket handler `passes` it writes an Http Forbidden response
   *  to the channel. To override that behavior, call this method with a function to handle
   *  the MessageEvent with custom behavior */
  def onPass(handler: (ChannelHandlerContext, MessageEvent) => Unit) = WebSocketHandler(path, intent, handler)

  private def location(req: NHttpRequest) = "ws://%s%s" format(req.getHeader(HOST), path)
}
