package unfiltered.netty.websockets

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import org.jboss.{netty => jnetty}
import jnetty.handler.codec.http.HttpHeaders
import jnetty.channel.{Channel, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.ChannelBuffer

object Plan {
  type SocketIntent = PartialFunction[SocketCallback, Unit]
  type Intent = PartialFunction[RequestBinding, SocketIntent]
  type Pass = (ChannelHandlerContext, MessageEvent) => Unit
}

private [websockets] object ProtocolRequestHeader extends StringHeader(HttpHeaders.Names.SEC_WEBSOCKET_PROTOCOL)
private [websockets] object SecKeyOne extends StringHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY1)
private [websockets] object SecKeyTwo extends StringHeader(HttpHeaders.Names.SEC_WEBSOCKET_KEY2)
private [websockets] object OriginRequestHeader extends StringHeader(HttpHeaders.Names.ORIGIN)
private [websockets] object ConnectionUpgrade {
  def unapply[T](req: HttpRequest[T]) =
    unfiltered.request.Connection(req).filter {
      _.equalsIgnoreCase(HttpHeaders.Values.UPGRADE)
    }
}

private [websockets] object UpgradeWebsockets {
  def unapply[T](req: HttpRequest[T]) =
    Upgrade(req).filter {
      _.equalsIgnoreCase(HttpHeaders.Values.WEBSOCKET)
    }.headOption.map { _ => req }
}

private [websockets] object WSLocation {
  def apply[T](r: HttpRequest[T]) = "ws://%s%s" format(Host(r).get, r.uri)
}

trait Plan extends SimpleChannelUpstreamHandler {

  import java.security.MessageDigest
  import jnetty.channel.{ChannelFuture, ChannelFutureListener,
                         ChannelStateEvent, ExceptionEvent}
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.websocket.{DefaultWebSocketFrame, WebSocketFrame,
                                             WebSocketFrameDecoder, WebSocketFrameEncoder}
  import jnetty.handler.codec.http.{HttpHeaders, HttpMethod, HttpRequest => NHttpRequest,
                                   HttpResponseStatus, HttpVersion,  DefaultHttpResponse,
                                   HttpResponse => NHttpResponse}

  import HttpHeaders._
  import HttpHeaders.Names.{CONNECTION, ORIGIN, HOST, UPGRADE, SEC_WEBSOCKET_LOCATION,
                            SEC_WEBSOCKET_ORIGIN, SEC_WEBSOCKET_PROTOCOL}
  import HttpHeaders.Values._

  import jnetty.util.CharsetUtil

  def intent: Plan.Intent

  def pass: Plan.Pass

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case http: NHttpRequest => upgrade(ctx, http, event)
      case _ => pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) =
    event.getChannel.close

  private def upgrade(ctx: ChannelHandlerContext, request: NHttpRequest, event: MessageEvent) = {
    val msg = ReceivedMessage(request, ctx, event)
    val binding = new RequestBinding(msg)
    binding match {
      case GET(ConnectionUpgrade(_) & UpgradeWebsockets(_)) =>
        if(intent.isDefinedAt(binding)) {
          val socketIntent = intent(binding)
          val response = msg.response(new DefaultHttpResponse(HttpVersion.HTTP_1_1,
                                                              new HttpResponseStatus(101, "Web Socket Protocol Handshake")))_
          def attempt = socketIntent.orElse({ case _ => () }: Plan.SocketIntent)

          val Protocol = new Responder[NHttpResponse] {
            def respond(res: HttpResponse[NHttpResponse]) {
              ProtocolRequestHeader(binding) match {
                case Some(protocol) =>
                  res.header(SEC_WEBSOCKET_PROTOCOL, protocol)
                case _ => ()
              }
            }
          }

          val HandShake = new Responder[NHttpResponse] {
            def respond(res: HttpResponse[NHttpResponse]) {
              (SecKeyOne(binding), SecKeyTwo(binding)) match {
                case (Some(k1), Some(k2)) =>
                  val buff = ChannelBuffers.buffer(16)
                  (k1 :: k2 :: Nil).foreach( k =>
                    buff.writeInt((k.replaceAll("[^0-9]", "").toLong / k.replaceAll("[^ ]", "").length).toInt)
                  )
                  buff.writeLong(request.getContent().readLong)
                  res.underlying.setContent(ChannelBuffers.wrappedBuffer(
                    MessageDigest.getInstance("MD5").digest(buff.array)))
                case _ => ()
              }
            }
          }

          val pipe = ctx.getChannel.getPipeline
          if(pipe.get("aggregator") != null) {
            pipe.remove("aggregator")
          }
          pipe.replace("decoder", "wsdecoder", new WebSocketFrameDecoder)

          ctx.getChannel.write(
            response(
              new HeaderName(UPGRADE)(Values.WEBSOCKET) ~>
              new HeaderName(CONNECTION)(Values.UPGRADE) ~>
              new HeaderName(SEC_WEBSOCKET_ORIGIN)(OriginRequestHeader(binding).get) ~>
              new HeaderName(SEC_WEBSOCKET_LOCATION)(WSLocation(binding)) ~>
              Protocol ~> HandShake)
          )
          ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) = {
              attempt(Close(WebSocket(ctx.getChannel)))
            }
          })
          pipe.replace("encoder", "wsencoder", new WebSocketFrameEncoder)
          attempt(Open(WebSocket(ctx.getChannel)))
          pipe.replace(this, ctx.getName, SocketPlan(socketIntent, pass))

        } else pass(ctx, event)
      case _ =>  pass(ctx, event)
    }
  }

  /** By default, when a websocket handler `passes` it writes an Http Forbidden response
   *  to the channel. To override that behavior, call this method with a function to handle
   *  the MessageEvent with custom behavior */
  def onPass(handler: Plan.Pass) = Planify(intent, handler)

}

class Planify(val intent: Plan.Intent, val pass: Plan.Pass) extends Plan

object Planify {
  import jnetty.channel.ChannelFutureListener
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.{HttpRequest => NHttpRequest, DefaultHttpResponse}
  import jnetty.handler.codec.http.HttpVersion.HTTP_1_1
  import jnetty.handler.codec.http.HttpResponseStatus.FORBIDDEN
  import jnetty.handler.codec.http.HttpHeaders._
  import jnetty.util.CharsetUtil

  def apply(intent: Plan.Intent, pass: Plan.Pass) = new Planify(intent, pass)

  /** Creates a WebSocketHandler that, when passes, will return a forbidden
   *  response to the client */
  def apply(intent: Plan.Intent): Plan =
    Planify(intent, { (ctx, event) =>
      event.getMessage match {
        case request: NHttpRequest =>
          val res = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN)
          res.setContent(ChannelBuffers.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8))
          setContentLength(res, res.getContent.readableBytes)
          ctx.getChannel.write(res).addListener(ChannelFutureListener.CLOSE)
        case msg =>
          error("Invalid type of event message (%s) for Plan pass handling" format msg.getClass.getName)
      }
   })
}

case class SocketPlan(intent: Plan.SocketIntent,
                      pass: Plan.Pass) extends SimpleChannelUpstreamHandler {
  import jnetty.channel.ExceptionEvent
  import jnetty.handler.codec.http.websocket.{WebSocketFrame}

  /** 0x00-0x7F typed frame becomes (UTF-8) Text
   0x80-0xFF typed frame becomes Binary */
  implicit def wsf2msg(wsf: WebSocketFrame): Msg =
    if(wsf.isText) Text(wsf.getTextData) else Binary(wsf.getBinaryData)

  def attempt = intent.orElse({ case _ => () }: Plan.SocketIntent)

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case f: WebSocketFrame => f.getType match {
        case 0xFF => ctx.getChannel.close
        case _ => attempt(Message(WebSocket(ctx.getChannel), f))
      }
      case _ => pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    event.getChannel.close
  }
}
