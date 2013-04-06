package unfiltered.netty.websockets

import unfiltered.request.{ GET, Host, HttpRequest }
import unfiltered.netty.{ ExceptionHandler, ReceivedMessage, RequestBinding }

import org.jboss.{ netty => jnetty }
import jnetty.buffer.ChannelBuffers
import jnetty.channel.{
  Channel, ChannelEvent, ChannelFuture, ChannelFutureListener,
  ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler
}
import jnetty.handler.codec.http.HttpHeaders
import jnetty.handler.codec.http.{ HttpRequest => NHttpRequest, DefaultHttpResponse }
import jnetty.handler.codec.http.websocketx.WebSocketServerHandshaker
import jnetty.util.CharsetUtil

/** Module defining function types used in the WebSockets module as well as
 *  function defaults. This object is deprecated in favor of the unfiltered.netty.websockets
 *  package object */
@deprecated("use unfiltered.netty.websocket package object instead")
object Plan {
  import jnetty.handler.codec.http.HttpVersion.HTTP_1_1
  import jnetty.handler.codec.http.HttpResponseStatus.FORBIDDEN

  /** The transition from an http request handling to websocket request handling.
   *  Note: This can not be an Async.Intent because RequestBinding is a Responder for HttpResponses */
  @deprecated("use unfiltered.netty.websocket.Intent")
  type Intent = PartialFunction[RequestBinding, SocketIntent]


  /** A SocketIntent is the result of a handler `lift`ing a request into
   *  the WebSocket protocol. WebSockets may be responded to asynchronously,
   * thus their handler will not need to return a value */
  @deprecated("use unfiltered.netty.websocket.SocketIntent")
  type SocketIntent = PartialFunction[SocketCallback, Unit]

  /** A pass handler type represents a means to forward a request upstream for
   *  unhandled patterns and protocol messages */
  @deprecated("use unfiltered.netty.websocket.PassHandler")
  type PassHandler = (ChannelHandlerContext, ChannelEvent) => Unit

  /** Equivalent of an HttpResponse's Pass function,
   *  a SocketIntent that does nothing */
  @deprecated("use unfiltered.netty.websocket.Pass")
  val Pass  = ({ case _ => () }: SocketIntent)

  /** A default implementation of a PassHandler which returns a HTTP
   *  forbidden response code to the channel before closing the channel */
  @deprecated("use unfiltered.netty.websocket.DefaultPassHandler")
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

/** Serves the same purpose of unfiltered.netty.ServerErrorResponse, which is to
 *  satisfy ExceptionHandler#onException, except that it is not specific to the HTTP protocol.
 *  It will simply log the Throwable and close the Channel */
trait CloseOnException { self: ExceptionHandler =>
  def onException(ctx: ChannelHandlerContext, t: Throwable) {
    t.printStackTrace
    ctx.getChannel.close
  }
}

private [websockets] object WSLocation {
  def apply[T](r: HttpRequest[T]) = "%s://%s%s" format(if(r.isSecure) "wss" else "ws", Host(r).get, r.uri)
}

/** Provides an extension point for netty ChannelHandlers that wish to
 *  support the WebSocket protocol */
trait Plan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  import jnetty.channel.ExceptionEvent
  import jnetty.handler.codec.http.websocketx.{
    WebSocketHandshakeException, WebSocketServerHandshakerFactory
  }
  import scala.util.control.Exception.catching

  def intent: Intent

  def pass: PassHandler

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case http: NHttpRequest => upgrade(ctx, http, event)
      case _ => pass(ctx, event)
    }

  /** `Lifts` an HTTP request into a WebSocket request.
   *  If the HTTP request handling intent is not defined or results
   *   in a Pass, the `pass` method will be invoked.
   *  If the HTTP request handling intent is
   *   defined for the HttpRequest, its SocketIntent return value will be used to
   *   evaluate WebSocket protocol requests
   *  If an HTTP intent filter is matched but this is not a websocket request,
   *    the `pass` method will be invoked. */
  private def upgrade(ctx: ChannelHandlerContext,
                      request: NHttpRequest,
                      event: MessageEvent) =
    new RequestBinding(ReceivedMessage(request, ctx, event)) match {
      case r @ GET(_) =>
        intent.orElse({ case _ => Pass }: Intent)(r) match {
          case Pass => pass(ctx, event)
          case socketIntent =>
            def attempt = socketIntent.orElse({ case _ => () }: SocketIntent)
            val factory =
              new WebSocketServerHandshakerFactory(
                WSLocation(r), null/* subprotocols */, false/* allowExtensions */)
            factory.newHandshaker(request) match {
              case null =>
                factory.sendUnsupportedWebSocketVersionResponse(ctx.getChannel())
              case shaker =>
                // handle handshake exceptions for the use case
                // of mounting an http plan on the same path
                // as a websocket handler
                catching(classOf[WebSocketHandshakeException]).either {
                  shaker.handshake(ctx.getChannel, request).addListener(new ChannelFutureListener {
                    def operationComplete(hf: ChannelFuture) {
                      val chan = hf.getChannel
                      attempt(Open(WebSocket(chan)))
                      chan.getCloseFuture.addListener(new ChannelFutureListener {
                        def operationComplete(cf: ChannelFuture) = {
                          attempt(Close(WebSocket(cf.getChannel)))
                        }
                      })
                      chan.getPipeline.replace(
                        Plan.this, ctx.getName,
                        SocketPlan(socketIntent, pass, shaker, Plan.this))
                    }
                  })
                }.fold({ _ => pass(ctx, event) }, identity)
            }
        }
      case _ => pass(ctx, event)
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
case class SocketPlan(intent: SocketIntent,
                      pass: PassHandler,
                      shaker: WebSocketServerHandshaker,
                      exceptions: ExceptionHandler)
  extends SimpleChannelUpstreamHandler {
  import jnetty.channel.ExceptionEvent
  import jnetty.handler.codec.http.websocketx.{
    CloseWebSocketFrame, PingWebSocketFrame,
    PongWebSocketFrame, TextWebSocketFrame,
    WebSocketFrame
  }

  def attempt = intent.orElse({ case _ => () }: SocketIntent)

  override def messageReceived(ctx: ChannelHandlerContext, event: MessageEvent) =
    event.getMessage match {
      case c: CloseWebSocketFrame =>
        shaker.close(ctx.getChannel, c)
      case p: PingWebSocketFrame =>
        ctx.getChannel.write(new PongWebSocketFrame(p.getBinaryData()))
      case t: TextWebSocketFrame =>
        attempt(Message(WebSocket(ctx.getChannel), Text(t.getText)))
      case f: WebSocketFrame =>
        // only text frames are supported
        pass(ctx, event)
    }

  override def exceptionCaught(ctx: ChannelHandlerContext, event: ExceptionEvent) {
    attempt(Error(WebSocket(ctx.getChannel), event.getCause))
    exceptions.onException(ctx, event.getCause)
  }
}

/** A Plan configured to handle Throwables by printing them before closing the channel */
@deprecated("Use Planify.apply or extend Plan", "0.6.8")
class Planify(val intent: Intent, val pass: PassHandler)
extends Plan with CloseOnException

/** A companion module for building web socket Plans. These plans have default CloseOnException
 *  error handling baked in. To your own ExceptionHandler, Instantiate an instance of Planify
 *  yourself mixing a custom ExceptionHandler implementation */
object Planify {
  /** Creates a WebSocket Plan with a custom PassHandler function */
  def apply(intent: Intent, pass: PassHandler) =
    new Planify(intent, pass)

  /** Creates a WebSocket Plan that, when `Pass`ing, will return a forbidden
   *  response to the client */
  def apply(intent: Intent): Plan =
    Planify(intent, DefaultPassHandler)
}
