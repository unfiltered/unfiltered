package unfiltered.netty

import unfiltered.util.{ HttpPortBindingShim, PlanServer, Port, RunnableServer }

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{
  ChannelHandler,
  ChannelInitializer,
  ChannelOption,
  ChannelPipeline,
  EventLoopGroup
}
import io.netty.channel.group.{ ChannelGroup, DefaultChannelGroup }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  HttpObjectAggregator,
  HttpRequestDecoder,
  HttpResponseEncoder
}
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.GlobalEventExecutor

import java.lang.{ Boolean => JBoolean, Integer => JInteger }
import java.net.{ InetSocketAddress, URL }

/** Default implementation of the Server trait. If you want to use a
 * custom pipeline factory it's better to extend Server directly. */
@deprecated("Use unfiltered.netty.Server", since="0.8.1")
case class Http(
  port: Int, host: String,
  handlers: List[() => ChannelHandler],
  beforeStopBlock: () => Unit,
  chunkSize: Int = 1048576)
  extends HttpServer with DefaultServerInit { self =>
  type ServerBuilder = Http

  def initializer: ChannelInitializer[SocketChannel] =
    new ServerInit(channels, handlers, chunkSize)

  override def makePlan(h: => ChannelHandler) =
    copy(handlers = { () => h } :: handlers)

  /** Convenience method for adding a HttpObjectAggregator to the
   *  pipeline. Supports chunked request bodies up to the specified
   *  maximum bytes. Without this aggregator, chunked requests will
   *  not not be handled. */
  def chunked(size: Int = 1048576) =
    copy(chunkSize = size)

  def handler(h: ChannelHandler) = makePlan(h)

  def beforeStop(block: => Unit) =
    copy(beforeStopBlock = { () => beforeStopBlock(); block })
}

/** Factory for creating Http servers */
@deprecated("Use unfiltered.netty.Server", since="0.8.1")
object Http {
  def apply(port: Int, host: String): Http =
    Http(port, host, Nil, () => ())
  def apply(port: Int): Http =
    Http(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int): Http =
    Http(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(Port.any)
}

/** An HTTP or HTTPS server */
@deprecated("Use unfiltered.netty.Server", since="0.8.1")
trait HttpServer extends NettyBase with PlanServer[ChannelHandler] {

  /** block of code to be invoked when the server is stopped,
   *  before connectons are closed */
  def beforeStopBlock: () => Unit

  /** list of functions which will produce a channel handler when invoked */
  def handlers: List[() => ChannelHandler]

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

  def resources(
    path: URL,
    cacheSeconds: Int = 60,
    passOnFail: Boolean = true) = {
    val resources = Resources(path, cacheSeconds, passOnFail)
    this.plan(resources).makePlan(new ChunkedWriteHandler)
  }
}

/** Base Netty server trait for http and websockets */
@deprecated("Use unfiltered.netty.Server", since="0.8.1")
trait NettyBase extends RunnableServer {
  /** port to listen on */
  val port: Int

  def portBindings = HttpPortBindingShim(host, port) :: Nil

  /** host to bind to */
  val host: String

  val url =  "http://%s:%d/" format(host, port)

  /** ChannelInitializer that initializes the server bootstrap */
  protected def initializer: ChannelInitializer[SocketChannel]

  // todo: previously used Executors.newCachedThreadPool()'s with NioServerSocketChannelFactory. investigate if this results in similar behavior

  /** EventLoopGroup associated with accepting client connections */
  protected val acceptor: EventLoopGroup = new NioEventLoopGroup()

  /** EventLoopGroup associated with handling client requests */
  protected val workers: EventLoopGroup = new NioEventLoopGroup()

  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup(
    "Netty Unfiltered Server Channel Group", GlobalEventExecutor.INSTANCE)

  /** Starts default server bootstrap */
  def start() = start(identity)

  /** Starts server with preBind callback called before connection binding */
  def start(preBind: ServerBootstrap => ServerBootstrap): ServerBuilder = {
    val bootstrap = preBind(new ServerBootstrap()
      .group(acceptor, workers)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(initializer)
      .childOption(ChannelOption.TCP_NODELAY, JBoolean.TRUE)
      .childOption(ChannelOption.SO_KEEPALIVE, JBoolean.TRUE)
      .childOption(ChannelOption.SO_RCVBUF, JInteger.valueOf(128 * 1024))
      .childOption(ChannelOption.SO_SNDBUF, JInteger.valueOf(128 * 1024))
      .option(ChannelOption.SO_REUSEADDR, JBoolean.TRUE)
      .option(ChannelOption.SO_BACKLOG, JInteger.valueOf(16384)))

    // binds channel and waits for this future until it is done, and rethrows the cause of the failure if this future failed.
    val binder = bootstrap.bind(new InetSocketAddress(host, port)).sync
    channels.add(binder.channel)
    // wait until server socket is closed
    // binder.channel().closeFuture().sync()
    this
  }

  def closeConnections() = {
    // Close any pending connections / channels (including server)
    channels.close.awaitUninterruptibly()
    this
  }

  def destroy() = {
    // Release NIO resources to the OS
    workers.shutdownGracefully()
    acceptor.shutdownGracefully()
    this
  }
}

@deprecated("Use unfiltered.netty.Server", since="0.8.1")
class ServerInit(
  protected val channels: ChannelGroup,
  protected val handlers: List[() => ChannelHandler],
  protected val chunkSize: Int)
  extends ChannelInitializer[SocketChannel] with DefaultServerInit {
  /** initialize the socket channel's pipeline */
  def initChannel(ch: SocketChannel) = complete(ch.pipeline)  
}

/**  HTTP Netty pipline builder. Uses Netty defaults: maxInitialLineLength 4096, maxHeaderSize 8192 and
 *   maxChunkSize 8192 */
@deprecated("Use unfiltered.netty.Server", since="0.8.1")
trait DefaultServerInit {

  /** A ChannelGroup used to manage cleanup with,
   *  in particular channel closing on server shutdown in #closeConnections() */
  protected def channels: ChannelGroup

  /** A list of functions which will produce a channel handler when invoked */
  protected def handlers: List[() => ChannelHandler]

  /** Size, in bytes, to aggregate http requests in chunks of */
  protected def chunkSize: Int

  protected def complete(line: ChannelPipeline) =
    (line.addLast("housekeeping", new HouseKeepingChannelHandler(channels))
     .addLast("decoder", new HttpRequestDecoder)
     .addLast("encoder", new HttpResponseEncoder)
     .addLast("chunker", new HttpObjectAggregator(chunkSize)) /: handlers.reverse.zipWithIndex) {
       case (pl, (handler, idx)) =>
         pl.addLast("handler-%s" format idx, handler())
    }.addLast("notfound", new NotFoundHandler)
}
