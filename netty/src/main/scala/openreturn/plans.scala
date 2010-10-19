package unfiltered.netty.openreturn

import org.jboss.netty.handler.codec.http.{DefaultHttpRequest,DefaultHttpResponse}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.netty._
import unfiltered.response.{ResponseFunction,NotFound}
import unfiltered.request.HttpRequest

object Plan {
  type Intent = PartialFunction[ChanneledRequestBinding, Unit]
}
/** A Netty Plan for roundtrip request handling. */
abstract class Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage().asInstanceOf[DefaultHttpRequest]
    val requestBinding = new ChanneledRequestBinding(request, ctx, e)
    if (intent.isDefinedAt(requestBinding)) {
      intent(requestBinding)
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}

class ChanneledRequestBinding(
    req: DefaultHttpRequest, 
    val context: ChannelHandlerContext,
    val event: MessageEvent) extends RequestBinding(req) {
  lazy val channel = messageEvent.getChannel
}
