package unfiltered.netty.websockets

import org.jboss.{netty => jnetty}

import jnetty.channel.{Channel, ChannelHandlerContext, MessageEvent, SimpleChannelUpstreamHandler}
import jnetty.buffer.ChannelBuffer

trait SocketCallback
case class Open(socket: WebSocket) extends SocketCallback
case class Close(socket: WebSocket) extends SocketCallback
case class Message(socket: WebSocket, msg: Msg) extends SocketCallback
case class Error(socket: WebSocket, err: Throwable) extends SocketCallback

sealed trait Msg
case class Text(txt: String) extends Msg
case class Binary(buf: jnetty.buffer.ChannelBuffer) extends Msg

case class WebSocket(channel: Channel) {
  import jnetty.handler.codec.http.websocket.DefaultWebSocketFrame

  def send(str: String) = channel.write(new DefaultWebSocketFrame(str))

  /** will throw an IllegalArgumentException if (type & 0x80 == 0) and the data is not
   * encoded in UTF-8 */
  def send(mtype: Int, buf: ChannelBuffer) = channel.write(new DefaultWebSocketFrame(mtype, buf))
}
