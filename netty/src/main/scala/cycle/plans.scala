package unfiltered.netty.cycle

import org.jboss.netty.handler.codec.http.{
  HttpRequest=>NHttpRequest,HttpResponse=>NHttpResponse,HttpChunk=>NHttpChunk}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.netty._
import unfiltered.response.{ResponseFunction, Pass}
import unfiltered.request.HttpRequest

object Plan {
  type Intent = unfiltered.Cycle.Intent[ReceivedMessage,NHttpResponse]
}
/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}
/** A Netty Plan for request cycle handling. */
trait Plan extends SimpleChannelUpstreamHandler with ExceptionHandler {
  def intent: Plan.Intent
  def catching(ctx: ChannelHandlerContext)(thunk: => Unit) {
    try { thunk } catch {
      case e => onException(ctx, e)
    }
  }

  private lazy val guardedIntent = intent.fold(
    (req: HttpRequest[ReceivedMessage]) =>
      req.underlying.context.sendUpstream(req.underlying.event),
    (req: HttpRequest[ReceivedMessage],
     rf: ResponseFunction[NHttpResponse]) =>
      executeResponse {
        catching(req.underlying.context) {
          req.underlying.respond(rf)
        }
      }
  )
  override def messageReceived(ctx: ChannelHandlerContext,
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
      case chunk:NHttpChunk => ctx.sendUpstream(e)
      case msg => error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
  }
  def executeIntent(thunk: => Unit)
  def executeResponse(thunk: => Unit)
  def shutdown()
}

class Planify(val intent: Plan.Intent)
extends Plan with ThreadPool with ServerErrorResponse

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
