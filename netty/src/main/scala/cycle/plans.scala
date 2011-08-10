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
  val executor = java.util.concurrent.Executors.newCachedThreadPool()
}
/** Object to facilitate Plan.Intent definitions. Type annotations
 *  are another option. */
object Intent {
  def apply(intent: Plan.Intent) = intent
}
/** A Netty Plan for request cycle handling. */
trait Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage() match {
      case req:NHttpRequest => req
      case msg => error("Unexpected message type from upstream: %s" format msg)
    }
    val requestBinding = new RequestBinding(ReceivedMessage(request, ctx, e))
    complete(intent)(requestBinding) match {
      case Pass => ctx.sendUpstream(e)
      case responseFunction =>
        Plan.executor.submit(new Runnable {
          def run {
            requestBinding.underlying.respond(responseFunction)
          }
        })
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
