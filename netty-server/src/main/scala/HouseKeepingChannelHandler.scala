package unfiltered.netty

import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.group.ChannelGroup

/**
 * Channel handler that keeps track of channels in a ChannelGroup for controlled
 * shutdown.
 */
@deprecated("Use unfiltered.netty.HouseKeeper", since="0.8.1")
@Sharable
class HouseKeepingChannelHandler(channels: ChannelGroup)
  extends HouseKeeper(channels)
