package unfiltered.netty

import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel._
import group.{ChannelGroup, DefaultChannelGroup}

class Server(val port: Int, val lastHandler: ChannelHandler) {
  val DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() + 1;
  val DEFAULT_EVENT_THREADS = DEFAULT_IO_THREADS * 4;
  
  private var bootstrap: ServerBootstrap = _
  
  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group")
  
  /** override this to provide an alternative pipeline factory */
  protected lazy val pipelineFactory: ChannelPipelineFactory = new ServerPipelineFactory(channels, lastHandler)

  def start() = {

    bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newFixedThreadPool(DEFAULT_IO_THREADS),
        Executors.newFixedThreadPool(DEFAULT_EVENT_THREADS)))

    bootstrap.setPipelineFactory(pipelineFactory)

    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    bootstrap.setOption("receiveBufferSize", 128 * 1024)
    bootstrap.setOption("sendBufferSize", 128 * 1024)
    bootstrap.setOption("reuseAddress", true)
    bootstrap.setOption("backlog", 16384)
    channels.add(bootstrap.bind(new InetSocketAddress(port)))
  }

  def stop() = {
    // Close any pending connections / channels (including server)
    channels.close.awaitUninterruptibly
    // Release NIO resources to the OS
    bootstrap.releaseExternalResources
  }
}

class ServerPipelineFactory(channels: ChannelGroup, lastHandler: ChannelHandler) extends ChannelPipelineFactory {
  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline

    line.addLast("houskeeping", new HouseKeepingChannelHandler(channels))
    line.addLast("decoder", new HttpRequestDecoder)
    line.addLast("encoder", new HttpResponseEncoder)
    line.addLast("handler", lastHandler)

    line
  }
}

/**
 * Channel handler that keeps track of channels in a ChannelGroup for controlled
 * shutdown.
 */
class HouseKeepingChannelHandler(channels: ChannelGroup) extends SimpleChannelUpstreamHandler {
  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    // Channels are automatically removed from the group on close
    channels.add(e.getChannel)
  }
}
