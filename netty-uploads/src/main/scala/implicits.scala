package unfiltered.netty.uploads

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
  implicit def netty3Req2netty4Req(v3Req: NHttpRequest): IOHttpRequest = {
    import scala.collection.JavaConversions._
    val v4Req = new IODefaultHttpReqeust(IOHttpVersion.valueOf(v3Req.getProtocolVersion.getText), IOHttpMethod.valueOf(v3Req.getMethod.getName), v3Req.getUri())
    v4Req.setChunked(v3Req.isChunked)
    v3Req.getHeaders.toList.foreach(h => v4Req.setHeader(h.getKey, h.getValue))
    v4Req.setContent(IOChannelBuffers.copiedBuffer(v3Req.getContent.array))
    v4Req
  }

  implicit def netty3Chunk2netty4Chunk(v3Chunk: NHttpChunk): IOHttpChunk = {
    new IODefaultHttpChunk(IOChannelBuffers.copiedBuffer(v3Chunk.getContent.array))
  }
  
  implicit def recvdMsg2netty3Req(msg: ReceivedMessage): NHttpRequest = {
    msg.request
  } 
}