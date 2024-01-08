package unfiltered.netty.websockets

import unfiltered.request.GET
import unfiltered.request.Host
import unfiltered.request.HttpRequest
import unfiltered.netty.ExceptionHandler
import unfiltered.netty.ReceivedMessage
import unfiltered.netty.RequestBinding
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator
import io.netty.handler.codec.http.websocketx.WebSocketFrameDecoder
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.util.ReferenceCountUtil
import scala.util.control.Exception.catching

/** Serves the same purpose of unfiltered.netty.ServerErrorResponse, which is to
 *  satisfy ExceptionHandler#onException, except that it is not specific to the HTTP protocol.
 *  It will simply log the Throwable and close the Channel */
trait CloseOnException { self: ExceptionHandler & ChannelInboundHandlerAdapter =>
  def onException(ctx: ChannelHandlerContext, t: Throwable): Unit = {
    t.printStackTrace()
    ctx.channel.close()
  }
}

private[websockets] object WSLocation {
  def apply[T](r: HttpRequest[T]): String = s"${if (r.isSecure) "wss" else "ws"}://${Host(r).get}${r.uri}"
}

/** Provides an extension point for netty ChannelHandlers that wish to
 *  support the WebSocket protocol */
trait Plan extends ChannelInboundHandlerAdapter with ExceptionHandler {

  /** specify PartialFunction[RequestBinding, SocketIntent] */
  def intent: Intent

  /** specify PartialFunction[RequestBinding, SocketIntent] */
  def pass: PassHandler

  /** Allow extensions to be used in the reserved bits of the web socket frame */
  def allowExtensions: Boolean = false

  final override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush()

  final override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case http: FullHttpRequest => upgrade(ctx, http)
      case unexpected => pass(ctx, unexpected)
    }

  /** `Lifts` an HTTP request into a WebSocket request.
   *  If the HTTP request handling intent is not defined or results
   *   in a Pass, the `pass` method will be invoked.
   *  If the HTTP request handling intent is
   *   defined for the HttpRequest, its SocketIntent return value will be used to
   *   evaluate WebSocket protocol requests
   *  If an HTTP intent filter is matched but this is not a websocket request,
   *    the `pass` method will be invoked. */
  private def upgrade(ctx: ChannelHandlerContext, request: FullHttpRequest) =
    if (!request.decoderResult.isSuccess()) pass(ctx, request)
    else
      new RequestBinding(ReceivedMessage(request, ctx, request)) match {
        case r @ GET(_) =>
          intent.orElse(PassAlong)(r) match {
            case Pass => pass(ctx, request)
            case socketIntent =>
              def attempt = socketIntent.lift
              val factory =
                new WebSocketServerHandshakerFactory(WSLocation(r), null /* subprotocols */, allowExtensions)
              factory.newHandshaker(request) match {
                case null =>
                  WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel)
                case shaker =>
                  // handle handshake exceptions for the use case
                  // of mounting an http plan on the same path
                  // as a websocket handler
                  catching(classOf[WebSocketHandshakeException]).either {
                    shaker
                      .handshake(ctx.channel, request)
                      .addListeners(
                        (hf: ChannelFuture) => {
                          val chan = hf.channel
                          attempt(Open(WebSocket(chan)))
                          chan.closeFuture.addListener((cf: ChannelFuture) => {
                            attempt(Close(WebSocket(cf.channel)))
                          })
                          chan.pipeline.replace(Plan.this, ctx.name, SocketPlan(socketIntent, pass, shaker, Plan.this))
                          // aggregate frames
                          chan.pipeline.addAfter(
                            chan.pipeline.context(classOf[WebSocketFrameDecoder]).name(),
                            "ws-frame-aggregator",
                            new WebSocketFrameAggregator(Integer.MAX_VALUE)
                          )
                        },
                        r.underlying.releaser
                      )
                  }.fold({ _ => pass(ctx, request) }, identity)
              }
          }
        case _ => pass(ctx, request)
      }

  /** By default, when a websocket handler `passes` it writes an Http Forbidden response
   *  to the channel in DefaultPassHandler. To override this behavior, call this method
   *   with a function to handle the ChannelEvent with custom behavior */
  def onPass(handler: PassHandler) = Planify(intent, handler)
}

/** The result of defined Intent should result in a SocketPlan value.
 *  SocketPlans are handlers for message frames in the WebSocket protocol.
 *  If an unexpected message is received by a SocketPlan, the request handling will automatically
 *  be delegated the PassHandler. As part of the WebSocket protocol, server errors should
 *  be reported to the websocket before closing. This is handled for you. */
case class SocketPlan(
  intent: SocketIntent,
  pass: PassHandler,
  shaker: WebSocketServerHandshaker,
  exceptions: ExceptionHandler
) extends ChannelInboundHandlerAdapter {

  def attempt = intent.lift

  final override def channelReadComplete(ctx: ChannelHandlerContext): Unit =
    ctx.flush()

  final override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    msg match {
      case c: CloseWebSocketFrame =>
        shaker.close(ctx.channel, c.retain())
      case p: PingWebSocketFrame =>
        ctx.channel.writeAndFlush(new PongWebSocketFrame(p.content.retain()))
      case t: TextWebSocketFrame =>
        attempt(Message(WebSocket(ctx.channel), Text(t.text))).foreach(_ => ReferenceCountUtil.release(t))
      case b: BinaryWebSocketFrame =>
        attempt(Message(WebSocket(ctx.channel), Binary(b.content))).foreach(_ => ReferenceCountUtil.release(b))
      case p: PongWebSocketFrame =>
      // unsolicited pong rec from client, a response is not expected
      // https://www.rfc-editor.org/rfc/rfc6455#section-5.5.3
      case c: ContinuationWebSocketFrame =>
        attempt(Continuation(WebSocket(ctx.channel), Fragment(c.content, c.isFinalFragment))).foreach(_ =>
          ReferenceCountUtil.release(c)
        )
      case f: WebSocketFrame =>
        // other frames are not supported at this time
        pass(ctx, f)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, throwable: Throwable): Unit = {
    attempt(Error(WebSocket(ctx.channel), throwable))
    exceptions.onException(ctx, throwable)
  }
}

/** A companion module for building web socket Plans. These plans have default CloseOnException
 *  error handling baked in. To your own ExceptionHandler, Instantiate an instance of Planify
 *  yourself mixing a custom ExceptionHandler implementation */
object Planify {
  @Sharable
  class Planned(val intent: Intent, val pass: PassHandler) extends Plan with CloseOnException

  /** Creates a WebSocket Plan with a custom PassHandler function */
  def apply(intentIn: Intent, passIn: PassHandler): Plan =
    new Planned(intentIn, passIn)

  /** Creates a WebSocket Plan that, when `Pass`ing, will return a forbidden
   *  response to the client */
  def apply(intentIn: Intent): Plan =
    new Planned(intentIn, DefaultPassHandler)
}
