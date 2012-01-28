package unfiltered.netty.request

import unfiltered.netty.ReceivedMessage

import org.jboss.{netty => jnetty}  // 3.x
import io.{netty => ionetty}        // 4.x

import jnetty.handler.codec.http.{HttpRequest => NHttpRequest}
import jnetty.handler.codec.http.{HttpChunk => NHttpChunk}

import ionetty.handler.codec.http.{HttpRequest => IOHttpRequest}
import ionetty.handler.codec.http.{HttpMethod => IOHttpMethod }
import ionetty.handler.codec.http.{HttpVersion => IOHttpVersion }
import ionetty.buffer.{ChannelBuffers => IOChannelBuffers}
import ionetty.handler.codec.http.{ DefaultHttpRequest =>IODefaultHttpReqeust }
import ionetty.handler.codec.http.{HttpChunk => IOHttpChunk}
import ionetty.handler.codec.http.{DefaultHttpChunk => IODefaultHttpChunk}

object Implicits {
  /** Convert a netty v3 request to a netty v4 request. 
      Useful so we can safely pass it to a netty v4 HttpPostRequestDecoder. */
  implicit def netty3Req2netty4Req(v3Req: NHttpRequest): IOHttpRequest = {
    import scala.collection.JavaConversions._
    val v4Req = new IODefaultHttpReqeust(IOHttpVersion.valueOf(v3Req.getProtocolVersion.getText), 
      IOHttpMethod.valueOf(v3Req.getMethod.getName), v3Req.getUri())
    v4Req.setChunked(v3Req.isChunked)
    v3Req.getHeaders.toList.foreach(h => v4Req.setHeader(h.getKey, h.getValue))
    v4Req.setContent(IOChannelBuffers.copiedBuffer(v3Req.getContent.array))
    v4Req
  }

  /** Converts a netty 3 chunk to a netty 4 chunk. */
  implicit def netty3Chunk2netty4Chunk(v3Chunk: NHttpChunk): IOHttpChunk = {
    new IODefaultHttpChunk(IOChannelBuffers.copiedBuffer(v3Chunk.getContent.array))
  }
}
