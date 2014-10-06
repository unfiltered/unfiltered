package unfiltered.netty

import io.netty.channel.{
  ChannelFutureListener,
  ChannelHandlerContext,
  ChannelFuture
}
import io.netty.channel.ChannelHandler.Sharable

import io.netty.handler.codec.{
  TooLongFrameException
}

import io.netty.handler.codec.http.{
  DefaultHttpResponse, DefaultFullHttpResponse, HttpContent, HttpMessage, HttpResponseStatus, 
  HttpObjectAggregator, HttpResponse, HttpVersion, HttpRequest, FullHttpMessage, HttpObjectDecoder
}

import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.HttpHeaders
import io.netty.handler.codec.http.HttpHeaders.Names._
import io.netty.handler.codec.http.HttpHeaders.Values._

/** Provide a simple way to provide a custom HttpObjectAggregator implementation to the Server.
 *  You may want to do this if for example you need to send an informative error message along 
 *  with a 413 status if the request is too large. */
@Sharable
class Chunker(size: Int) extends HttpObjectAggregator(size)

/** Allows sending a response body along with the 413 response */
class PoliteChunker(size: Int, responseBody: String, contentType: String = "text/plain") extends Chunker(size) {

  /** Send a 413 status and a custom response body */
  override def handleOversizedMessage(ctx: ChannelHandlerContext, oversized: HttpMessage) = oversized match {

    case _: HttpRequest =>

      val body = responseBody.getBytes("utf-8")
      val contentLength = body.size
      val tooLarge = new DefaultFullHttpResponse(
        HttpVersion.HTTP_1_1, 
        HttpResponseStatus.REQUEST_ENTITY_TOO_LARGE, 
        Unpooled.copiedBuffer(body))
      tooLarge.headers().set(CONTENT_LENGTH, contentLength)
    
      // send back a 413 and close the connection
      val future = ctx.writeAndFlush(tooLarge).addListener(new ChannelFutureListener() {
        def operationComplete(future: ChannelFuture) {
          if (!future.isSuccess()) {
            System.err.println("Failed to send a 413 Request Entity Too Large. " + future.cause())
            ctx.close()
          }
        }
      })

      // If the client started to send data already, close because it's impossible to recover.
      // If 'Expect: 100-continue' and keep-alive is off, no need to leave the connection open.
      if (oversized.isInstanceOf[FullHttpMessage] ||
              (!HttpHeaders.is100ContinueExpected(oversized) && !HttpHeaders.isKeepAlive(oversized))) {
        future.addListener(ChannelFutureListener.CLOSE)
      }

      // If an oversized request was handled properly and the connection is still alive
      // (i.e. rejected 100-continue). the decoder should prepare to handle a new message.
      if (HttpHeaders.is100ContinueExpected(oversized)) {
        Option(ctx.pipeline().get(classOf[HttpObjectDecoder])).map { 
          _.reset()
        }
      }
    
    case _: HttpResponse =>
      ctx.close();
      throw new TooLongFrameException("Response entity too large: " + oversized)
    
    case _ =>
      throw new IllegalStateException()
  }
}

/** Chunker factory for helping make Chunkers */
object Chunker {
  def apply(size: Int) = () => new Chunker(size)

  object Polite {
    def apply(size: Int, responseBody: String, contentType: String = "text/plain") = 
      () => new PoliteChunker(size, responseBody, contentType)
  }
}
