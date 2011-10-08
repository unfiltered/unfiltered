package unfiltered.netty.websockets

import unfiltered.request._
import unfiltered.response._
import unfiltered.netty._

import org.jboss.{netty => jnetty}

import jnetty.channel.{Channel, ChannelEvent, ChannelFuture, ChannelFutureListener,
                       ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.{ChannelBuffer, ChannelBuffers}
import jnetty.handler.codec.http.HttpHeaders
import jnetty.handler.codec.http.{HttpRequest => NHttpRequest,
                                  HttpResponse => NHttpResponse,
                                  DefaultHttpResponse}
import jnetty.handler.codec.http.HttpVersion.HTTP_1_1
import jnetty.handler.codec.http.HttpResponseStatus.FORBIDDEN
import jnetty.handler.codec.http.HttpHeaders.setContentLength

import jnetty.util.CharsetUtil

object Plan {
  /** The trasition from an http request handling to websocket request handling.
   *  Note: This can not be an Async.Intent because RequestBinding is a Responder for HttpResponses */
  type Intent =
    PartialFunction[RequestBinding, SocketIntent]

  /** WebSockets may be responded to asynchronously, thus their handler does not need to return */
  type SocketIntent =
    PartialFunction[SocketCallback, Unit]

  /** Equivalent of an HttpResponse's Pass fn.
   *  A SocketIntent that does nothing */
  val Pass  = ({
    case _ => ()
  }: SocketIntent)

  type PassHandler = (ChannelHandlerContext, ChannelEvent) => Unit

  val DefaultPassHandler = ({ (ctx, event) =>
    event match {
      case me: MessageEvent =>
        me.getMessage match {
          case request: NHttpRequest =>
            val res = new DefaultHttpResponse(HTTP_1_1, FORBIDDEN)
            res.setContent(ChannelBuffers.copiedBuffer(res.getStatus.toString, CharsetUtil.UTF_8))
            setContentLength(res, res.getContent.readableBytes)
            ctx.getChannel.write(res).addListener(ChannelFutureListener.CLOSE)
          case msg =>
            error("Invalid type of event message (%s) for Plan pass handling".format(
              msg.getClass.getName))
        }
      case _ => () // we really only care about MessageEvents but need to support the more generic ChannelEvent
    }
   }: PassHandler)
}

trait CloseOnException { self: ExceptionHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
    t.printStackTrace
    ctx.getChannel.close
  }
}

/** a light wrapper around both Sec-WebSocket-Draft + Sec-WebSocket-Version headers */
private [websockets] object Version {
  def apply[T](req: HttpRequest[T]) = IetfDrafts.SecWebSocketDraft.unapply(req).orElse(
    IetfDrafts.SecWebSocketVersion.unapply(req)
  )
}

/** See also http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14 */
private [websockets] object IetfDrafts {

  /** Server handshake as described in
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-4.2.2 */
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
   /** Prior to draft 04, the websocket spec provided an optional draft header
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-03#section-10.8 */
  object SecWebSocketDraft extends StringHeader("Sec-WebSocket-Draft")

  // response headers
  object WebSocketAccept extends HeaderName("Sec-WebSocket-Accept")
  object SecWebSocketVersionName extends HeaderName("Sec-WebSocket-Version")
}

private [websockets] object HixieDrafts {
  import java.security.MessageDigest
  import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}

  /** Sec-WebSocket-Key(1/2) included in the hixie drafts and later removed in ietf drafts
   *  see the later in drafts 00-03
   *  http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-03#section-1.3 */
  object SecKeyOne extends StringHeader("Sec-WebSocket-Key1")
  object SecKeyTwo extends StringHeader("Sec-WebSocket-Key2")

  case class Handshake(binding: RequestBinding) extends Responder[NHttpResponse] {
    def respond(res: HttpResponse[NHttpResponse]) {
      (HixieDrafts.SecKeyOne(binding), HixieDrafts.SecKeyTwo(binding)) match {
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
    unfiltered.request.Connection(req).filter {
      !_.split(",").map(_.trim).filter(_.equalsIgnoreCase(HttpHeaders.Values.UPGRADE)).isEmpty
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

trait Plan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  import jnetty.channel.{ChannelStateEvent, ExceptionEvent}
  import jnetty.handler.codec.http.websocket.{DefaultWebSocketFrame, WebSocketFrame,
                                             WebSocketFrameDecoder => LegacyWebSocketFrameDecoder,
                                             WebSocketFrameEncoder => LegacyWebSocketFrameEncoder}
  import jnetty.handler.codec.http.{HttpHeaders, HttpMethod, HttpRequest => NHttpRequest,
                                   HttpResponseStatus, HttpVersion,  DefaultHttpResponse}
  import HttpHeaders._
  import HttpHeaders.Names.{CONNECTION, ORIGIN, HOST, UPGRADE}
  import HttpHeaders.Values._

  val SecWebSocketLocation = "Sec-WebSocket-Location"
  val SecWebSocketOrigin = "Sec-WebSocket-Origin"
  val SecWebSocketProtocol = "Sec-WebSocket-Protocol"

  def intent: Plan.Intent

  def pass: Plan.PassHandler

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case http: NHttpRequest => upgrade(ctx, http, event)
      case _ => pass(ctx, event)
    }

  private def upgrade(ctx: ChannelHandlerContext, request: NHttpRequest,
                      event: MessageEvent) = {
    val msg = ReceivedMessage(request, ctx, event)

    val binding = new RequestBinding(msg)

    val version = Version(binding)
    binding match {
      case GET(ConnectionUpgrade(_) & UpgradeWebsockets(_)) =>
        intent.orElse({ case _ => Plan.Pass }: Plan.Intent)(binding) match {
          case Plan.Pass =>
            pass(ctx, event)
          case socketIntent =>

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

            if(pipe.get("aggregator") != null) pipe.remove("aggregator")

            val legacy = version match {
              case None => true
              case Some(earlier)
                if(earlier.toInt < 4) => true
              case Some(recent) => false
            }

            pipe.replace("decoder", "wsdecoder",
                         if(legacy) new LegacyWebSocketFrameDecoder
                         else new Draft14WebSocketFrameDecoder)

            ctx.getChannel.write(
              response(
                new HeaderName(UPGRADE)(Values.WEBSOCKET) ~>
                new HeaderName(CONNECTION)(Values.UPGRADE) ~>
                new HeaderName(SecWebSocketOrigin)(OriginRequestHeader(binding).getOrElse("*")) ~>
                new HeaderName(SecWebSocketLocation)(WSLocation(binding)) ~>
                Protocol ~> (
                  if(legacy) HixieDrafts.Handshake(binding)
                  else {
                    IetfDrafts.Handshake(IetfDrafts.SecWebSocketKey.unapply(binding).get) ~>
                    IetfDrafts.SecWebSocketVersionName(version.getOrElse("0"))
                  })
              )
            )

            ctx.getChannel.getCloseFuture.addListener(new ChannelFutureListener {
              def operationComplete(future: ChannelFuture) = {
                attempt(Close(WebSocket(ctx.getChannel)))
              }
            })

            pipe.replace("encoder", "wsencoder",
                         if(legacy) new LegacyWebSocketFrameEncoder
                         else new Draft14WebSocketFrameEncoder)

            attempt(Open(WebSocket(ctx.getChannel)))

            pipe.replace(this, ctx.getName, SocketPlan(socketIntent, pass))

        }
      case _ =>
        pass(ctx, event)
    }
  }

  /** By default, when a websocket handler `passes` it writes an Http Forbidden response
   *  to the channel. To override that behavior, call this method with a function to handle
   *  the ChannelEvent with custom behavior */
  def onPass(handler: Plan.PassHandler) = Planify(intent, handler)

}

class Planify(val intent: Plan.Intent, val pass: Plan.PassHandler) extends Plan with CloseOnException

object Planify {
  import jnetty.buffer.ChannelBuffers
  import jnetty.handler.codec.http.{HttpRequest => NHttpRequest, DefaultHttpResponse}
  import jnetty.handler.codec.http.HttpHeaders._
  import jnetty.util.CharsetUtil

  def apply(intent: Plan.Intent, pass: Plan.PassHandler) = new Planify(intent, pass)

  /** Creates a WebSocketHandler that, when `Passing`, will return a forbidden
   *  response to the client */
  def apply(intent: Plan.Intent): Plan =
    Planify(intent, Plan.DefaultPassHandler)
}

case class SocketPlan(intent: Plan.SocketIntent,
                      pass: Plan.PassHandler) extends SimpleChannelUpstreamHandler {
  import jnetty.channel.{ChannelFuture, ChannelFutureListener, ExceptionEvent}
  import jnetty.handler.codec.http.websocket.WebSocketFrame

  /** 0x00-0x7F typed frame becomes (UTF-8) Text
   0x80-0xFF typed frame becomes Binary */
  implicit def wsf2msg(wsf: WebSocketFrame): Msg =
    if(wsf.isText) Text(wsf.getTextData) else Binary(wsf.getBinaryData)

  def attempt = intent.orElse({ case _ => () }: Plan.SocketIntent)

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case c @ ClosingFrame(_) =>
        ctx.getChannel.write(c).addListener(new ChannelFutureListener {
          override def operationComplete(f: ChannelFuture) = ctx.getChannel.close
        })
      case p @ PingFrame(_) => ctx.getChannel.write(p)
      case p @ PongFrame(_) => ctx.getChannel.write(p)
      case f: WebSocketFrame => f.getType match {
        case 0xFF => /* binary not impl */()
        case _    => attempt(Message(WebSocket(ctx.getChannel), f))
      }
      case _ => pass(ctx, event)
    }

  // todo: if there's an error we may want to bubble this upstream
  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) = {
    event.getCause.printStackTrace
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    event.getChannel.close
  }
}
