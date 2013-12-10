package unfiltered.netty.cycle

import io.netty.handler.codec.http.{
  FullHttpRequest,
  HttpContent,
  HttpRequest  => NHttpRequest,
  HttpResponse => NHttpResponse }
import io.netty.channel.{ ChannelHandlerContext, ChannelInboundHandlerAdapter }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.HttpResponseStatus._
import io.netty.handler.codec.http.HttpVersion._

import unfiltered.netty._
import unfiltered.response.{ResponseFunction, Pass}
import unfiltered.request.HttpRequest
import unfiltered.util.control.NonFatal

object Plan {
  type Intent = unfiltered.Cycle.Intent[ReceivedMessage,NHttpResponse]
}

/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}

/** A Netty Plan for request cycle handling. */
@Sharable // this indicates that the handler is stateless and be called without syncronization
trait Plan extends ChannelInboundHandlerAdapter with ExceptionHandler {
  def intent: Plan.Intent
  def catching(ctx: ChannelHandlerContext)(thunk: => Unit) {
    try { thunk } catch {
      case NonFatal(e) => onException(ctx, e)
    }
  }

  private lazy val guardedIntent = intent.fold(
    (req: HttpRequest[ReceivedMessage]) =>
      req.underlying.context.fireChannelRead(req.underlying.message),
    (req: HttpRequest[ReceivedMessage],
     rf: ResponseFunction[NHttpResponse]) =>
      executeResponse {
        catching(req.underlying.context) {
          req.underlying.respond(rf)
        }
      }
  )
  
  override def channelRead(ctx: ChannelHandlerContext, msg: java.lang.Object ): Unit =
    msg match {
      case req: FullHttpRequest =>
        catching(ctx) {
          executeIntent {
            catching(ctx) {
              guardedIntent(
                new RequestBinding(ReceivedMessage(req, ctx, msg))
              )
            }
          }
        }
      case chunk: HttpContent => ctx.fireChannelRead(chunk)
      case ue => sys.error("Unexpected message type from upstream: %s"
                           .format(ue))
    }

  /*override def messageReceived(ctx: ChannelHandlerContext,
                               e: MessageEvent) {
    e.getMessage() match {
      case req:NHttpRequest =>
        catching(ctx) {
          executeIntent {
            catching(ctx) {
              guardedIntent(
                new RequestBinding(ReceivedMessage(req, ctx, e))
              )
            }
          }
        }
      case chunk: HttpContent => ctx.sendUpstream(e)
      case msg => sys.error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
  }*/
  def executeIntent(thunk: => Unit)
  def executeResponse(thunk: => Unit)
  def shutdown()
}

object Planify {
  def apply(intentIn: Plan.Intent) = new Plan with ThreadPool with ServerErrorResponse {
    val intent = intentIn
  }
}
