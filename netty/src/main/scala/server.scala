package unfiltered.netty

import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel._
import group.DefaultChannelGroup

class Server(val port: Int, lastHandler: ChannelHandler) {
  val DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() + 1;
  val DEFAULT_EVENT_THREADS = DEFAULT_IO_THREADS * 4;

  private var socketChannel : Channel = _
  private var bootstrap: ServerBootstrap = _

  private val pipelineFactory = new ServerPipelineFactory(lastHandler)

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
    socketChannel = bootstrap.bind(new InetSocketAddress(port))
  }

  def stop() = {
    // Unbind the server socket
    socketChannel.unbind.awaitUninterruptibly
    // Close any pending connections / channels
    pipelineFactory.houseKeeping.channelGroup.close.awaitUninterruptibly
    // Release NIO resources to the OS
    bootstrap.releaseExternalResources
  }
}

class ServerPipelineFactory(val lastHandler: ChannelHandler) extends ChannelPipelineFactory {

  val houseKeeping = new HouseKeepingChannelHandler

  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline

    line.addLast("houskeeping", houseKeeping)
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
class HouseKeepingChannelHandler extends SimpleChannelUpstreamHandler {
  val channelGroup = new DefaultChannelGroup("Netty Unfiltered Channel group")

  override def channelOpen(ctx: ChannelHandlerContext, e: ChannelStateEvent) = {
    // Channels are automatically removed from the group on close
    channelGroup.add(e.getChannel)
  }

}
