package unfiltered.netty.cycle

import org.jboss.netty.handler.codec.http.{
  HttpRequest=>NHttpRequest,HttpResponse=>NHttpResponse}
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
trait Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage().asInstanceOf[NHttpRequest]
    val requestBinding = new RequestBinding(ReceivedMessage(request, ctx, e))
    
    intent.orElse({ case _ => Pass }: Plan.Intent)(requestBinding) match {
      case Pass => ctx.sendUpstream(e)
      case responseFunction => requestBinding.underlying.respond(responseFunction)
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
