package unfiltered.netty

import unfiltered.util.{RunnableServer,PlanServer}
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{
  HttpRequestDecoder, HttpResponseEncoder, HttpChunkAggregator}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.channel._
import group.{ChannelGroup, DefaultChannelGroup}
import unfiltered._
import java.util.concurrent.atomic.AtomicInteger

/** Default implementation of the Server trait. If you want to use a
 * custom pipeline factory it's better to extend Server directly. */
case class Http(port: Int, host: String,
                handlers: List[() => ChannelHandler],
                beforeStopBlock: () => Unit)
extends HttpServer { self =>
  type ServerBuilder = Http

  def pipelineFactory: ChannelPipelineFactory =
    new ServerPipelineFactory(channels, handlers)

  def makePlan(h: => ChannelHandler) =
    Http(port, host, { () => h } :: handlers, beforeStopBlock)
  def handler(h: ChannelHandler) = makePlan(h)
  def beforeStop(block: => Unit) =
    Http(port, host, handlers, { () => beforeStopBlock(); block })
}

object Http {
  def apply(port: Int, host: String): Http =
    Http(port, host, Nil, () => ())
  def apply(port: Int): Http =
    Http(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int): Http =
    Http(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(unfiltered.util.Port.any)
}

/** An HTTP or HTTPS server */
trait HttpServer extends Server with PlanServer[ChannelHandler] {
  def beforeStopBlock: () => Unit
  def handlers: List[() => ChannelHandler]
  /** Convenience method for adding a HttpChunkAggregator to the
   *  pipeline. Supports chunked request bodies up to the specified
   *  maximum. Without this aggregater, chunked requests will not
   *  not be handled. */
  def chunked(maxContentLength: Int = 1048576) =
    makePlan(new HttpChunkAggregator(maxContentLength))
  def stop() = {
    beforeStopBlock()
    closeConnections()
    handlers.foreach { handler =>
      handler() match {
        case p: unfiltered.netty.cycle.Plan => p.shutdown()
        case _ => ()
      }
    }
    destroy()
  }
  def resources(path: java.net.URL,
                cacheSeconds: Int = 60,
                passOnFail: Boolean = true) = {
    val resources = Resources(path, cacheSeconds, passOnFail)
    this.plan(resources).makePlan(new ChunkedWriteHandler)
  }
}

/** Base Netty server trait for http, websockets... */
trait Server extends RunnableServer {
  val port: Int
  val host: String
  val url =  "http://%s:%d/" format(host, port)
  protected def pipelineFactory: ChannelPipelineFactory

  private var bootstrap: ServerBootstrap = _

  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group")

  def start(): ServerBuilder = {
    bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
        Executors.newCachedThreadPool(),
        Executors.newCachedThreadPool()
      )
    )
    bootstrap.setPipelineFactory(pipelineFactory)

    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setOption("child.keepAlive", true)
    bootstrap.setOption("receiveBufferSize", 128 * 1024)
    bootstrap.setOption("sendBufferSize", 128 * 1024)
    bootstrap.setOption("reuseAddress", true)
    bootstrap.setOption("backlog", 16384)
    channels.add(bootstrap.bind(new InetSocketAddress(host, port)))
    this
  }

  def closeConnections() = {
    // Close any pending connections / channels (including server)
    channels.close.awaitUninterruptibly
    this
  }
  def destroy() = {
    // Release NIO resources to the OS
    bootstrap.releaseExternalResources
    this
  }
}

class ServerPipelineFactory(val channels: ChannelGroup,
                            val handlers: List[() => ChannelHandler])
extends ChannelPipelineFactory with DefaultPipelineFactory {
  def getPipeline(): ChannelPipeline = complete(Channels.pipeline)
}

/**  HTTP Netty pipline builder. Uses Netty defaults: maxHeaderSize 8192 and
 *   maxChunkSize 8192 */
trait DefaultPipelineFactory {
  def channels: ChannelGroup
  def handlers: List[() => ChannelHandler]
  protected def complete(line: ChannelPipeline) = {
    line.addLast("housekeeping", new HouseKeepingChannelHandler(channels))
    line.addLast("decoder", new HttpRequestDecoder)
    line.addLast("encoder", new HttpResponseEncoder)
    handlers.reverse.zipWithIndex.foreach { case (handler, idx) =>
      line.addLast("handler-%s" format idx, handler())
    }
    line.addLast("notfound", new NotFoundHandler)
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

class NotFoundHandler extends SimpleChannelUpstreamHandler {
  import org.jboss.netty.channel.ChannelFutureListener
  import org.jboss.netty.handler.codec.http.{
    DefaultHttpRequest, DefaultHttpResponse, HttpResponseStatus
  }

  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val version = e.getMessage().asInstanceOf[DefaultHttpRequest].getProtocolVersion
    val response = new DefaultHttpResponse(version, HttpResponseStatus.NOT_FOUND)
    val future = e.getChannel.write(response)

    future.addListener(ChannelFutureListener.CLOSE)
  }
}
