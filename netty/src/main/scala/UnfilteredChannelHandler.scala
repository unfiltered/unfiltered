package unfiltered.netty

import unfiltered.PassingIntent
import _root_.unfiltered.response.{NotFound, ResponseFunction, Pass}
import org.jboss.netty.channel._
import org.jboss.netty.handler.codec.http.HttpResponseStatus._
import org.jboss.netty.handler.codec.http.HttpVersion._
import unfiltered.request.HttpRequest
import org.jboss.netty.handler.codec.http.{DefaultHttpRequest, DefaultHttpResponse}

/**
 * ChannelHandler which responds via Unfiltered functions.
 * 
 */
abstract class UnfilteredChannelHandler extends SimpleChannelUpstreamHandler with PassingIntent[DefaultHttpRequest] {

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {

    val request = e.getMessage().asInstanceOf[DefaultHttpRequest]
    val response = new DefaultHttpResponse(HTTP_1_1, OK)

    response.setHeader("Server", "Scala Netty Unfiltered Server")
    val ch = request.getHeader("Connection")
    val keepAlive = request.getProtocolVersion match {
      case HTTP_1_1 => !"close".equalsIgnoreCase(ch)
      case HTTP_1_0 => "Keep-Alive".equals(ch)
    }

    val requestBinding = new RequestBinding(request)
    val responseBinding = new ResponseBinding(response)

    attempt(requestBinding) match {
      case Pass => NotFound(responseBinding)
      case response_function => response_function(responseBinding)
    }

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

  override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) {
  }

}
