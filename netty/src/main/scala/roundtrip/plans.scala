package unfiltered.netty.roundtrip

import org.jboss.netty.handler.codec.http.{DefaultHttpRequest,DefaultHttpResponse}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.netty._
import unfiltered.response.{ResponseFunction,NotFound}
import unfiltered.request.HttpRequest

object Plan {
  type Intent = unfiltered.Roundtrip.Intent[DefaultHttpRequest]
}
/** The default Netty Plan. (There may be other kinds of channel handlers?) */
abstract class Plan extends SimpleChannelUpstreamHandler {
  def intent: Plan.Intent
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val request = e.getMessage().asInstanceOf[DefaultHttpRequest]
    val requestBinding = new RequestBinding(request)


    def respond[T](rf: ResponseFunction) {
      val response = new DefaultHttpResponse(HTTP_1_1, OK)

      response.setHeader("Server", "Scala Netty Unfiltered Server")
      val ch = request.getHeader("Connection")
      val keepAlive = request.getProtocolVersion match {
        case HTTP_1_1 => !"close".equalsIgnoreCase(ch)
        case HTTP_1_0 => "Keep-Alive".equals(ch)
      }

      val responseBinding = new ResponseBinding(response)
      
      rf(responseBinding)

      responseBinding.getOutputStream.close
      if (keepAlive) {
        response.setHeader("Connection", "Keep-Alive")
        response.setHeader("Content-Length", response.getContent().readableBytes());
      } else {
        response.setHeader("Connection", "close")
      }

      val future = e.getChannel.write(response)
      if (!keepAlive) {
        future.addListener(ChannelFutureListener.CLOSE)
      }
    }
    if (intent.isDefinedAt(requestBinding)) {
      intent(requestBinding) match {
        case response_function => respond(response_function)
      }
    } else {
      respond(NotFound)
    }
  }
}

class Planify(val intent: Plan.Intent) extends Plan

object Planify {
  def apply(intent: Plan.Intent) = new Planify(intent)
}

case class Channeled(cf: org.jboss.netty.channel.Channel => Unit)
     extends unfiltered.response.ResponseFunction {
  def apply[T](res: unfiltered.response.HttpResponse[T]) = res
}
