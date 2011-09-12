package unfiltered.netty.websockets

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import org.jboss.{netty => jnetty}
import jnetty.handler.codec.http.HttpHeaders
import jnetty.channel.{Channel, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.ChannelBuffer

object Plan {
  /** The trasition from an http request handling to websocket request handling */
  type Intent = PartialFunction[RequestBinding, SocketIntent]
  /** WebSockets may be responded to asynchronously, thus their handler does not need to return */
  type SocketIntent = PartialFunction[SocketCallback, Unit]
  type Pass = (ChannelHandlerContext, MessageEvent) => Unit
}

/** a light wrapper around both Sec-WebSocket-Draft + Sec-WebSocket-Version headers */
object Version {
  def apply[T](req: HttpRequest[T]) = EarlyDrafts.SecWebSocketDraft.unapply(req).orElse(
    Draft14.SecWebSocketVersion.unapply(req)
  )
}

/** See also http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14 */
object Draft14 {

  /** Server handshake as described in
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-4.2.2
   *  @draft 14 */
  object Handshake {
    import java.security.MessageDigest
    import org.apache.commons.codec.binary.Base64.encodeBase64
    val GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    val Sha1 = "SHA1"
    def sign(key: String): Array[Byte] =
      encodeBase64(MessageDigest.getInstance(Sha1).digest((key.trim + GUID).getBytes))
    def apply(key: String) = WebSocketAccept(new String(sign(key)))
  }

  // request headers
  object SecWebSocketKey extends StringHeader("Sec-WebSocket-Key")
  object SecWebSocketVersion extends StringHeader("Sec-WebSocket-Version")

  // response headers
  object WebSocketAccept extends HeaderName("Sec-WebSocket-Accept")
  object SecWebSocketVersionName extends HeaderName("Sec-WebSocket-Version")
}

object EarlyDrafts {
  import java.security.MessageDigest
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.{HttpResponse => NHttpResponse, HttpRequest => NHttpRequest}

  /** Sec-WebSocket-Key(1/2) as described in drafts 00-03
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-03#section-1.3 */
  object SecKeyOne extends StringHeader("Sec-WebSocket-Key1")

  object SecKeyTwo extends StringHeader("Sec-WebSocket-Key2")

  /** Prior to draft 04, the websocket spec provided an optional draft header
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-03#section-10.8 */
  object SecWebSocketDraft extends StringHeader("Sec-WebSocket-Draft")

  case class Handshake(binding: RequestBinding) extends Responder[NHttpResponse] {
    def respond(res: HttpResponse[NHttpResponse]) {
      (EarlyDrafts.SecKeyOne(binding), EarlyDrafts.SecKeyTwo(binding)) match {
        case (Some(k1), Some(k2)) =>
          val buff = ChannelBuffers.buffer(16)
          (k1 :: k2 :: Nil).foreach( k =>
            buff.writeInt((k.replaceAll("[^0-9]", "").toLong /
                           k.replaceAll("[^ ]", "").length).toInt)
           )
          buff.writeLong(binding.underlying.request.getContent().readLong)
          res.underlying.setContent(ChannelBuffers.wrappedBuffer(
            MessageDigest.getInstance("MD5").digest(buff.array)
          ))
        case _ => ()
      }
    }
  }
}

private [websockets] object ProtocolRequestHeader
       extends StringHeader(HttpHeaders.Names.SEC_WEBSOCKET_PROTOCOL)

private [websockets] object OriginRequestHeader
       extends StringHeader(HttpHeaders.Names.ORIGIN)

private [websockets] object ConnectionUpgrade {
  def unapply[T](req: HttpRequest[T]) =
    unfiltered.request.Connection(req).filter { c =>
      !c.split(",").map(_.trim).filter(_.equalsIgnoreCase(HttpHeaders.Values.UPGRADE)).isEmpty
    }
}

private [websockets] object UpgradeWebsockets {
  def unapply[T](req: HttpRequest[T]) =
    Upgrade(req).filter { u =>
      u.equalsIgnoreCase(HttpHeaders.Values.WEBSOCKET)
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
  import HttpHeaders.Names.{CONNECTION, ORIGIN, HOST, UPGRADE}
  import HttpHeaders.Values._

  import jnetty.util.CharsetUtil

  val SecWebSocketLocation = "Sec-WebSocket-Location"
  val SecWebSocketOrigin = "Sec-WebSocket-Origin"
  val SecWebSocketProtocol = "Sec-WebSocket-Protocol"

  def intent: Plan.Intent

  def pass: Plan.Pass

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case http: NHttpRequest => upgrade(ctx, http, event)
      case _ => pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    event.getChannel.close
  }

  private def upgrade(ctx: ChannelHandlerContext, request: NHttpRequest, event: MessageEvent) = {
    val msg = ReceivedMessage(request, ctx, event)
    val binding = new RequestBinding(msg)
    val version = Version(binding)
    binding match {
      case GET(ConnectionUpgrade(_) & UpgradeWebsockets(_)) =>
        if(intent.isDefinedAt(binding)) {
          val socketIntent = intent(binding)
          val response = msg.response(
            new DefaultHttpResponse(
              HttpVersion.HTTP_1_1,
              new HttpResponseStatus(101, "Web Socket Protocol Handshake")
            )
          )_

          def attempt = socketIntent.orElse({ case _ => () }: Plan.SocketIntent)

          val Protocol = new Responder[NHttpResponse] {
            def respond(res: HttpResponse[NHttpResponse]) {
              ProtocolRequestHeader(binding) match {
                case Some(protocol) =>
                  res.header(SecWebSocketProtocol, protocol)
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
              new HeaderName(SecWebSocketOrigin)(OriginRequestHeader(binding).getOrElse("*")) ~>
              new HeaderName(SecWebSocketLocation)(WSLocation(binding)) ~>
              Protocol ~> (version match {
                case None =>
                  EarlyDrafts.Handshake(binding)
                case Some(earlier) if(earlier.toInt < 4) =>
                  EarlyDrafts.Handshake(binding)
                case Some(recent) =>
                  Draft14.Handshake(Draft14.SecWebSocketKey.unapply(binding).get) ~>
                  Draft14.SecWebSocketVersionName(version.getOrElse("0"))
              })
            )
          )
          ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
            def operationComplete(future: ChannelFuture) = {
              attempt(Close(WebSocket(ctx.getChannel)))
            }
          })

          pipe.replace("encoder", "wsencoder", new WebSocketFrameEncoder)
          attempt(Open(WebSocket(ctx.getChannel)))
          pipe.replace(this, ctx.getName, SocketPlan(socketIntent, pass))

        } else {
          pass(ctx, event)
        }
      case _ =>
        pass(ctx, event)
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

  /** Creates a WebSocketHandler that, when `Passing`, will return a forbidden
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
          error("Invalid type of event message (%s) for Plan pass handling".format(
            msg.getClass.getName))
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
        case 0xFF =>
          ctx.getChannel.close
        case _ =>
          attempt(Message(WebSocket(ctx.getChannel), f))
      }
      case _ =>
        pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    event.getChannel.close
  }
}
