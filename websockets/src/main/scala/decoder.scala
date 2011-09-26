package unfiltered.netty.websockets

import org.jboss.netty.handler.codec.http.websocket.{WebSocketFrameDecoder, WebSocketFrame, DefaultWebSocketFrame}

import org.jboss.netty.buffer.{ChannelBuffer, ChannelBuffers}
import org.jboss.netty.channel.{Channel, ChannelHandlerContext, ChannelFuture, ChannelFutureListener}
import org.jboss.netty.handler.codec.frame.TooLongFrameException
import org.jboss.netty.handler.codec.replay.{ReplayingDecoder, VoidEnum}

import java.nio.charset.Charset

object Draft14WebSocketFrameDecoder {

  implicit def i2b(i: Int) = i.asInstanceOf[Byte]

  val MaxFrameSize = 16384

  val ValidFinCodes = Seq(0,1)
  val ValidRsvCode = 0

  val ValidMask = 1

  // Control frame opcodes
  // http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-5.5

  val OpContinuation = 0x0
  val OpText = 0x1
  val OpBin = 0x2
  val OpClose = 0x8
  val OpPing = 0x9
  val OpPong = 0xa

  val StringCharset = Charset.forName("UTF-8").name
}

// todo: perhaps we may want to pass in a handler fn
// to be invoken on an error condition
class Draft14WebSocketFrameDecoder
extends ReplayingDecoder[VoidEnum] {
  import Draft14WebSocketFrameDecoder._

  private var receivedClosingHandshake = false

  override protected def decode(
    ctx: ChannelHandlerContext, channel: Channel,
    buffer: ChannelBuffer, state: VoidEnum): AnyRef = {

    // Decode a frame otherwise.
    // first byte [fin, rsv1, rsv2, rsv3, opcode]
    val first = buffer.readByte
    val fin = (first >> 7) & 1
    val (rsv1, rsv2, rsv3) = (
      (first >> 6) & 1,
      (first >> 5) & 1,
      (first >> 4) & 1
    )
    val opcode = first & 0xf

    if(!ValidFinCodes.contains(fin)) {
      error("Invalid fin code %s" format fin)
    }

    // MUST be 0 unless an extension is negotiated which defines meanings
    // for non-zero values
    // - http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-5.2
    //if(!Seq(rsv1, rsv2, rsv3).filter(_ != ValidRsvCode).isEmpty) {
    //  error("Invalid rsv code")
    //}

    if(opcode > 0x7 && fin == 0) {
      // Control frames MAY be injected in the middle of a fragmented
      // message.  Control frames themselves MUST NOT be fragmented.
      // http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-5.4, rule 3
      error("control frames can not be fragmented")
    }

    // note: All control frames MUST have a payload length of 125 bytes or less

    val second = buffer.readByte()

    (fin, opcode) match {
      case (1, OpContinuation) =>
        null
      case (1, OpText) =>
        textFrame(second, buffer)
      case (1, OpBin) =>
        binaryFrame(second, buffer)
      case (1, OpClose) =>
        controlFrame(channel, buffer, 0x88) { msg =>
          ClosingFrame(msg)
        }
      case (1, OpPing) =>
        controlFrame(channel, buffer, 0x89) { msg =>
          PongFrame(msg)
        }
      case (1, OpPong) =>
        controlFrame(channel, buffer, 0x8A) { msg =>
          PingFrame(msg)
        }
      case (f, o) =>
        // unimplemented extension opt codes
        buffer.skipBytes(actualReadableBytes())
        null
    }
  }

  private def controlFrame(channel: Channel, buffer: ChannelBuffer, header: Byte)
                                (f: ChannelBuffer => ControlFrame) = {
    buffer.skipBytes(actualReadableBytes)
    val msg = channel.getConfig().getBufferFactory().getBuffer(
      buffer.order, 1)
    msg.writeByte(header)
    f(msg)
  }

  private def binaryFrame(second: Int, buffer: ChannelBuffer) =
    dataFrame(second, buffer) { decoded =>
      new DefaultWebSocketFrame(1, ChannelBuffers.wrappedBuffer(decoded))
    }

  private def textFrame(second: Int, buffer: ChannelBuffer) =
    dataFrame(second, buffer) { decoded =>
      new DefaultWebSocketFrame(new String(decoded, StringCharset))
    }

  private def dataFrame(second: Int, buffer: ChannelBuffer)
                                (f: Array[Byte] => WebSocketFrame): WebSocketFrame = {
    val ridx = buffer.readerIndex()

    val rbytes = actualReadableBytes()

    val mask = (second >> 7) & 1

    // A masked frame MUST have the field frame-masked set to 1,
    // - http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-5.3
    if (mask != ValidMask) error("Client mask %s not 1" format mask)

    var len = (second & 0x7f) match {
      case less if(less < 126) => less
      case 126 => buffer.readShort()
      case more => buffer.readLong().toInt
    }
    if(len > MaxFrameSize) throw new TooLongFrameException()

    // all frames from the client are masked by a 32-bit mask key
    // present if mask is set to 1, absent if 0
    val maskKey = buffer.readBytes(4)

    val payload = buffer.readBytes(actualReadableBytes)

    //val unmasked = unmask(len, text, maskKey)

    f(unmask(len, payload, maskKey))
  }

  /* http://tools.ietf.org/html/draft-ietf-hybi-thewebsocketprotocol-14#section-5.3 */
  private def unmask(len: Int, data: ChannelBuffer, maskKey: ChannelBuffer) = {
    val unmasked = new Array[Byte](len)
    for(i <- 0 until len) {
      unmasked(i) = (data.getByte(i) ^ maskKey.getByte(i % 4))
    }
    unmasked
  }
}
