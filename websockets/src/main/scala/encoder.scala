package unfiltered.netty.websockets


import org.jboss.netty.handler.codec.http.websocket.{WebSocketFrame,
                                                     WebSocketFrameDecoder}

import org.jboss.netty.buffer.ChannelBuffer
import org.jboss.netty.channel.{Channel,ChannelHandlerContext}
import org.jboss.netty.channel.ChannelHandler.Sharable
import org.jboss.netty.handler.codec.oneone.OneToOneEncoder

import org.jboss.netty.util.CharsetUtil

class Draft14WebSocketFrameEncoder extends OneToOneEncoder {

  override protected def encode(ctx:ChannelHandlerContext,
                            channel: Channel, msg: AnyRef): AnyRef =
    msg match {
      case c: ControlFrame =>
        // control frames may be written as is
        val data = c.getBinaryData
        val encoded =
          channel.getConfig().getBufferFactory().getBuffer(data.order(), data.readableBytes)
        encoded.writeBytes(data, data.readerIndex, data.readableBytes)
        encoded
      case frame: WebSocketFrame =>
        val `type` = frame.getType
        if(frame.isText) {
          val data = frame.getBinaryData
          val rbytes = data.readableBytes
          val blen = {
            if(rbytes < 126) rbytes + 2       // 1 header byte + 1 len byte
            else if(rbytes == 126) rbytes + 4 // 1 header byte + 3 len bytes (1 + 2 extra)
            else rbytes + 10                  // 1 header byte + 9 len bytes (1 + 8 extra)
          }

          val encoded =
            channel.getConfig().getBufferFactory().getBuffer(
              data.order(), blen)

          // write msg header
          encoded.writeByte(0x81.asInstanceOf[Byte])

          // write len
          if(rbytes < 126) encoded.writeByte(rbytes)
          else if (rbytes == 126) {
            encoded.writeByte(126)
            encoded.writeByte(126 >> 8)
            encoded.writeByte(126 & 0xFF)
          } else {
            encoded.writeByte(127)
            encoded.writeLong(rbytes)
          }

          // write data
          encoded.writeBytes(data, data.readerIndex, data.readableBytes)
          encoded

        } throw new UnsupportedOperationException(
          "Binary frames not yet supported"
        )

      case unknownMsg => new IllegalArgumentException(
        "Message must be a WebSocket or Control frame. %s" format unknownMsg
      )
    }
}
