package unfiltered.netty.websockets

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import org.jboss.{netty => jnetty}
import jnetty.channel.{Channel, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.ChannelBuffer

object Plan {
  type SocketIntent = PartialFunction[SocketCallback, Unit]
  type Intent = PartialFunction[RequestBinding, SocketIntent]
  type Pass = (ChannelHandlerContext, MessageEvent) => Unit
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
  import jnetty.handler.codec.http
  import HttpHeaders._
  import HttpHeaders.Names.{CONNECTION, ORIGIN, HOST, UPGRADE, SEC_WEBSOCKET_LOCATION,
                            SEC_WEBSOCKET_ORIGIN, SEC_WEBSOCKET_PROTOCOL,
                            SEC_WEBSOCKET_KEY1, SEC_WEBSOCKET_KEY2}
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

  object ConnectionUpgrade {
    def unapply[T](r: HttpRequest[T]) = r match {
      case unfiltered.request.Connection(values) =>
        if(values.exists { _.equalsIgnoreCase(Values.UPGRADE) }) Some(r)
        else None
    }
  }

  object UpgradeWebsockets {
    def unapply[T](r: HttpRequest[T]) = r match {
      case Upgrade(values) =>
        if(values.exists { _.equalsIgnoreCase(Values.WEBSOCKET) }) Some(r)
        else None
    }
  }

  object WSLocation {
    def apply[T](r: HttpRequest[T]) = "ws://%s%s" format(Host(r)(0), r.requestURI)
  }

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
              new RequestHeader(SEC_WEBSOCKET_PROTOCOL)(binding) match {
                case Seq(protocol) =>
                  res.addHeader(SEC_WEBSOCKET_PROTOCOL, protocol)
                case _ => ()
              }
            }
          }

          val HandShake = new Responder[NHttpResponse] {
            def respond(res: HttpResponse[NHttpResponse]) {
              (new RequestHeader(SEC_WEBSOCKET_KEY1)(binding) :: new RequestHeader(SEC_WEBSOCKET_KEY2)(binding) :: Nil) match {
                case List(k1) :: List(k2) :: Nil =>
                  val buff = ChannelBuffers.buffer(16)
                  (k1 :: k2 :: Nil).foreach( k =>
                    buff.writeInt((k.replaceAll("[^0-9]", "").toLong / k.replaceAll("[^ ]", "").length).toInt)
                  )
                  buff.writeLong(request.getContent().readLong)
                  res.underlying.setContent(ChannelBuffers.wrappedBuffer(MessageDigest.getInstance("MD5").digest(buff.array)))
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
              new HeaderName(SEC_WEBSOCKET_ORIGIN)(new RequestHeader(ORIGIN)(binding).head) ~>
              new HeaderName(SEC_WEBSOCKET_LOCATION)(WSLocation(binding)) ~>
              Protocol ~> HandShake)
          )
          ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) =
              attempt(Close(WebSocket(ctx.getChannel)))
          })
          pipe.replace("encoder", "wsencoder", new WebSocketFrameEncoder)
          attempt(Open(WebSocket(ctx.getChannel)))
          pipe.replace(this, ctx.getName, SocketPlan(socketIntent, pass))

        } else pass(ctx, event)
        case _ => pass(ctx, event)
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
        case msg => error("Invalid type of event message (%s) for Plan pass handling" format msg.getClass.getName)
      }
    })
}

case class SocketPlan(intent: Plan.SocketIntent,
                      pass: Plan.Pass)
  extends SimpleChannelUpstreamHandler {
  import jnetty.channel.ExceptionEvent
  import jnetty.handler.codec.http.websocket.{WebSocketFrame}

  /** 0x00-0x7F typed frame becomes (UTF-8) Text
      0x80-0xFF typed frame becomes Binary */
  implicit def wsf2msg(wsf: WebSocketFrame): Msg =
    if(wsf.isText) Text(wsf.getTextData) else Binary(wsf.getBinaryData)

  def attempt = intent.orElse({ case _ => () }: Plan.SocketIntent)

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case frame: WebSocketFrame => attempt(Message(WebSocket(ctx.getChannel), frame))
      case _ => pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    event.getChannel.close
  }
}
