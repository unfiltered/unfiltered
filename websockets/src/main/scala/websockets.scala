package unfiltered.websockets

import org.jboss.{netty => netty}

import netty.channel.{Channel, SimpleChannelUpstreamHandler}
import netty.buffer.ChannelBuffer

trait SocketCallback
case class Open(socket: WebSocket) extends SocketCallback
case class Close(socket: WebSocket) extends SocketCallback
case class Message(socket: WebSocket, msg: Msg) extends SocketCallback
case class Error(socket: WebSocket, err: Throwable) extends SocketCallback

sealed trait Msg
case class Text(txt: String) extends Msg
case class Binary(buf: netty.buffer.ChannelBuffer) extends Msg

case class WebSocket(c: Channel) {
  import netty.handler.codec.http.websocket.DefaultWebSocketFrame
  
  def send(str: String) = c.write(new DefaultWebSocketFrame(str))
  
  /** will throw an IllegalArgumentException if (type & 0x80 == 0) and the data is not
   * encoded in UTF-8 */
  def send(mtype: Int, buf: ChannelBuffer) = c.write(new DefaultWebSocketFrame(mtype, buf))
}

class WebSocketHandler(path: String, intent: PartialFunction[SocketCallback, Unit]) 
  extends SimpleChannelUpstreamHandler {
  
  import java.security.MessageDigest
  import netty.channel.{ChannelFuture, ChannelFutureListener, ChannelHandlerContext, 
                        ChannelStateEvent, ExceptionEvent, MessageEvent}
  import netty.buffer.ChannelBuffers
  import netty.handler.codec.http.websocket.{DefaultWebSocketFrame, WebSocketFrame, 
                                             WebSocketFrameDecoder, WebSocketFrameEncoder}
  import netty.handler.codec.http.{HttpHeaders, HttpMethod, HttpRequest => NHttpRequest, 
                                   HttpResponseStatus, HttpVersion, DefaultHttpResponse,
                                   HttpResponse => NHttpResponse}
  import netty.handler.codec.http
  import HttpHeaders._
  import HttpHeaders.Names._
  import HttpHeaders.Values._
  import HttpMethod._
  import HttpResponseStatus._
  import HttpVersion._
  import netty.util.CharsetUtil
  
  /** 0x00-0x7F typed frame becomes (UTF-8) Text
      0x80-0xFF typed frame becomes Binary */
  implicit def wsf2msg(wsf: WebSocketFrame): Msg = 
    if(wsf.isText) Text(wsf.getTextData) else Binary(wsf.getBinaryData)
  
  /** attempt to handle the intent (moving towards unfiltered interface) */
  def attempt(request: SocketCallback) =
    (try {
      intent(request)
    } catch {
      case m: MatchError => ()
    })
  
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) = e.getMessage match {
    case http: NHttpRequest => handshake(ctx, http)
    case ws: WebSocketFrame => frame(ctx, ws)
  }
  
  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
    attempt(Error(WebSocket(ctx.getChannel), e.getCause))
    e.getChannel.close
  }
   
  private def handshake(ctx: ChannelHandlerContext, req: NHttpRequest) = req.getMethod match {
    case GET => req.getUri match {
      case p: String if(p.equals(path)) =>
        if (Values.UPGRADE.equalsIgnoreCase(req.getHeader(CONNECTION)) && 
            WEBSOCKET.equalsIgnoreCase(req.getHeader(Names.UPGRADE))) {
          val res = new DefaultHttpResponse(HTTP_1_1, 
                                            new HttpResponseStatus(101, "Web Socket Protocol Handshake"))
          
          def head(k: String, v: String) = res.addHeader(k, v)
          def headIfDefined(k: String) = if(req.getHeader(k) != null) head(k, req.getHeader(k))
          
          head(Names.UPGRADE, WEBSOCKET)
          head(CONNECTION, Values.UPGRADE)        
          head(SEC_WEBSOCKET_ORIGIN, req.getHeader(ORIGIN))
          head(SEC_WEBSOCKET_LOCATION, location(req))
          headIfDefined(SEC_WEBSOCKET_PROTOCOL) 
          
          if (req.containsHeader(SEC_WEBSOCKET_KEY1) && req.containsHeader(SEC_WEBSOCKET_KEY2)) {
            val in = ChannelBuffers.buffer(16)
            List(req.getHeader(SEC_WEBSOCKET_KEY1), req.getHeader(SEC_WEBSOCKET_KEY2)).foreach( k =>
              in.writeInt((k.replaceAll("[^0-9]", "").toLong / k.replaceAll("[^ ]", "").length).toInt)
            )
            in.writeLong(req.getContent().readLong)
            res.setContent(ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(in.array)))
          }
        
          val p = ctx.getChannel.getPipeline
          p.remove("aggregator")
          p.replace("decoder","wsdecoder", new WebSocketFrameDecoder)
          ctx.getChannel.write(res)
          ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) =
             attempt(Close(WebSocket(ctx.getChannel)))
          })
          p.replace("encoder", "wsencoder", new WebSocketFrameEncoder)
          
          attempt(Open(WebSocket(ctx.getChannel)))
          
        } else {
          forbid(ctx, req)
        }
      /* some other path */
      case _ => forbid(ctx, req)
    }
    /* some other http method */
    case _ => forbid(ctx, req)
  }
  
  private def frame(ctx: ChannelHandlerContext, frame: WebSocketFrame) =
    attempt(Message(WebSocket(ctx.getChannel), frame))
  
  private def forbid(ctx: ChannelHandlerContext, req: NHttpRequest) =
    sendHttpResponse(ctx, req, new DefaultHttpResponse(HTTP_1_1, FORBIDDEN))
  
  private def sendHttpResponse(ctx: ChannelHandlerContext, req: NHttpRequest, res: NHttpResponse) = {
    if(res.getStatus.getCode != 200) {
      res.setContent(ChannelBuffers.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8))
      setContentLength(res, res.getContent.readableBytes)
    }
    val f = ctx.getChannel.write(res)
    if(!isKeepAlive(req) || res.getStatus.getCode != 200) {
      f.addListener(ChannelFutureListener.CLOSE)
    }
  }
  
  private def location(req: NHttpRequest) = "ws://%s%s" format(req.getHeader(HttpHeaders.Names.HOST), path)
}
