package unfiltered.netty.cycle

import org.jboss.netty.handler.codec.http.{
  HttpRequest=>NHttpRequest,HttpResponse=>NHttpResponse}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.netty._
import unfiltered.response.{ResponseFunction, Pass}
import unfiltered.request.HttpRequest
import unfiltered.Cycle.Intent.complete

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
  override def messageReceived(ctx: ChannelHandlerContext,
                               e: MessageEvent) {
    val request = e.getMessage() match {
      case req:NHttpRequest => req
      case msg => error("Unexpected message type from upstream: %s"
                        .format(msg))
    }
    val requestBinding =
      new RequestBinding(ReceivedMessage(request, ctx, e))
    def catching(thunk: => Unit) {
      try { thunk } catch {
        case e => onException(ctx, e)
      }
    }
    executeIntent { catching {
      intent.lift(requestBinding).getOrElse(Pass) match {
        case Pass => ctx.sendUpstream(e)
        case responseFunction =>
          executeResponse { catching {
            requestBinding.underlying.respond(responseFunction)
          } } 
      }
    } }
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
