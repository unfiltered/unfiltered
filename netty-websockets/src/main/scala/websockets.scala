package unfiltered.netty.websockets

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.channel.ChannelFuture

trait SocketCallback
case class Open(socket: WebSocket) extends SocketCallback
case class Close(socket: WebSocket) extends SocketCallback
case class Message(socket: WebSocket, msg: Msg) extends SocketCallback
case class Error(socket: WebSocket, err: Throwable) extends SocketCallback
case class Continuation(socket: WebSocket, fragment: Fragment) extends SocketCallback

sealed trait Msg
case class Fragment(buf: ByteBuf, last: Boolean) extends Msg
case class Text(txt: String) extends Msg
case class Binary(buf: ByteBuf) extends Msg

case class WebSocket(channel: Channel) {
  def send(str: String): ChannelFuture = channel.writeAndFlush(new TextWebSocketFrame(str))

  /** will throw an IllegalArgumentException if (type & 0x80 == 0)
   * and the data is not encoded in UTF-8 */
  def send(buf: ByteBuf): ChannelFuture = channel.writeAndFlush(
    new BinaryWebSocketFrame(buf)
  )
}
