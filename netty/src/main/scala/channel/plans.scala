package unfiltered.netty.channel

import org.jboss.netty.handler.codec.http.{DefaultHttpRequest,DefaultHttpResponse}
import org.jboss.netty.channel._
import unfiltered.netty._
import unfiltered.response.NotFound
import unfiltered.request.HttpRequest

object Plan {
  type Intent = PartialFunction[RequestBinding, Unit]
}
/** A Netty Plan for request only handling. */
trait Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage().asInstanceOf[DefaultHttpRequest]
    val messageBinding = new RequestBinding(ReceivedMessage(request, ctx, e))
    if (intent.isDefinedAt(messageBinding)) {
      intent(messageBinding)
    } else {
      messageBinding.underlying.respond(NotFound)
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}
