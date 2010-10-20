package unfiltered.netty.channel

import org.jboss.netty.handler.codec.http.{DefaultHttpRequest,DefaultHttpResponse}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.netty._
import unfiltered.response.{ResponseFunction,NotFound}
import unfiltered.request.HttpRequest

object Plan {
  type Intent = PartialFunction[RecievedMessageBinding, Unit]
}
/** A Netty Plan for request only handling. */
abstract class Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage().asInstanceOf[DefaultHttpRequest]
    val messageBinding = new RecievedMessageBinding(request, ctx, e)
    if (intent.isDefinedAt(messageBinding)) {
      intent(messageBinding)
    } else {
      messageBinding.respond(NotFound)
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}

class RecievedMessageBinding(
    req: DefaultHttpRequest, 
    val context: ChannelHandlerContext,
    val event: MessageEvent) extends RequestBinding(req) {
  import org.jboss.netty.handler.codec.http.{HttpResponse => NHttpResponse}
  lazy val channel = event.getChannel

  // ultimately this should be the foundation for roundtrip plans too
  def response[T <: NHttpResponse](res: T)(rf: ResponseFunction) =
    rf(new ResponseBinding(res)).underlying
  // should be based on version of incoming request
  val defaultResponse = response(new DefaultHttpResponse(HTTP_1_1, OK))_
  def respond(rf: ResponseFunction) = 
    channel.write(
      defaultResponse(rf)
    ).addListener(ChannelFutureListener.CLOSE) // should be based on incoming request
}
