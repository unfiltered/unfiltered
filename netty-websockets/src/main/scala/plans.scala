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

/** A companion module for building web socket Plans. These plans have default CloseOnException
 *  error handling baked in. To your own ExceptionHandler, Instantiate an instance of Planify
 *  yourself mixing a custom ExceptionHandler implementation */
object Planify {
  /** Creates a WebSocket Plan with a custom PassHandler function */
  def apply(intentIn: Intent, passIn: PassHandler) =
    new Plan with CloseOnException {
      val intent = intentIn
      val pass = passIn
    }

  /** Creates a WebSocket Plan that, when `Pass`ing, will return a forbidden
   *  response to the client */
  def apply(intentIn: Intent): Plan =
    new Plan with CloseOnException {
      val intent = intentIn
      val pass = DefaultPassHandler
    }
}
