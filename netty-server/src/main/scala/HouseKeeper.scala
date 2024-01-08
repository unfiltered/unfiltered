package unfiltered.netty

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.group.ChannelGroup

/**
 * Channel handler that keeps track of channels in a ChannelGroup for controlled
 * shutdown.
 */
@Sharable
class HouseKeeper(channels: ChannelGroup) extends ChannelInboundHandlerAdapter {
  override def channelActive(ctx: ChannelHandlerContext): Unit = {
    // Channels are automatically removed from the group on close
    channels.add(ctx.channel)
    ctx.fireChannelActive()
  }
}
