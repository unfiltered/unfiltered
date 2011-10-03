package unfiltered.netty.websockets

import org.jboss.netty.handler.codec.http.websocket.DefaultWebSocketFrame
import org.jboss.netty.buffer.ChannelBuffer

abstract class ControlFrame(cb: ChannelBuffer) extends DefaultWebSocketFrame(0, cb)
case class ClosingFrame(cb: ChannelBuffer) extends ControlFrame(cb)
case class PingFrame(cb: ChannelBuffer) extends ControlFrame(cb)
case class PongFrame(cb: ChannelBuffer) extends ControlFrame(cb)
