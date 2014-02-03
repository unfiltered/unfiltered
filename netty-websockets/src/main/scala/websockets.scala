package unfiltered.netty.websockets

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.handler.codec.http.websocketx.{ BinaryWebSocketFrame, TextWebSocketFrame }

trait SocketCallback
case class Open(socket: WebSocket) extends SocketCallback
case class Close(socket: WebSocket) extends SocketCallback
case class Message(socket: WebSocket, msg: Msg) extends SocketCallback
case class Error(socket: WebSocket, err: Throwable) extends SocketCallback

sealed trait Msg
case class Text(txt: String) extends Msg
case class Binary(buf: ByteBuf) extends Msg

case class WebSocket(channel: Channel) {
  def send(str: String) = channel.writeAndFlush(new TextWebSocketFrame(str))

  /** will throw an IllegalArgumentException if (type & 0x80 == 0)
   * and the data is not encoded in UTF-8 */
  def send(buf: ByteBuf) = channel.writeAndFlush(
    new BinaryWebSocketFrame(buf)
  )
}
