package unfiltered.netty

import unfiltered.util.{ RunnableServer, PlanServer }

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{
  ChannelFutureListener,
  ChannelHandler,
  ChannelHandlerContext,
  ChannelInboundHandlerAdapter,
  ChannelInitializer,
  ChannelOption,
  ChannelPipeline,
  EventLoopGroup
}
import io.netty.channel.ChannelHandler.Sharable
import io.netty.channel.group.{ ChannelGroup, DefaultChannelGroup }
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  DefaultHttpResponse,
  HttpContent,
  HttpMessage,
  HttpObjectAggregator,
  HttpRequestDecoder,
  HttpResponseEncoder,
  HttpResponseStatus
}
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.ReferenceCountUtil
import io.netty.util.concurrent.{ EventExecutor, ImmediateEventExecutor }

import java.lang.{ Boolean => JBoolean, Integer => JInteger }
import java.net.{ InetSocketAddress, URL }

/** Default implementation of the Server trait. If you want to use a
 * custom pipeline factory it's better to extend Server directly. */
case class Http(
  port: Int, host: String,
  handlers: List[() => ChannelHandler],
  beforeStopBlock: () => Unit,
  chunkSize: Int                                 = Http.DefaultChunkSize,
  acceptorGroup: () => EventLoopGroup            = () => new NioEventLoopGroup(),
  workerGroup:   () => EventLoopGroup            = () => new NioEventLoopGroup(),
  // there are typically 3 choices
  // GlobalEventExecutor, ImmediateEventExecutor, SingleThreadEventExecutor
  houseKeepingEventExecutor: () => EventExecutor = () => ImmediateEventExecutor.INSTANCE)
  extends HttpServer with DefaultServerInit { self =>
  type ServerBuilder = Http

  def initializer: ChannelInitializer[SocketChannel] =
    new ServerInit(channels, handlers, chunkSize)

  /** specifies the EventLoopGroup used by the ServerBootstrap's for accepting incoming connections */
  def acceptor(group: => EventLoopGroup) = copy(acceptorGroup = () => group)

  /** specifies the EventLoopGroup used by the ServerBootstrap's for handling
   *  requests after having registered with the acceptor */
  def workers(group: => EventLoopGroup) = copy(workerGroup = () => group)

  /** specifies the EventExecuture used by the pre-pipelined housekeeping channel group */
  def houseKeepingExecutor(exec: => EventExecutor) =
    copy(houseKeepingEventExecutor = () => exec)

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
object Http {
  val DefaultChunkSize = 1048576
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
trait Server extends RunnableServer {
  /** port to listen on */
  val port: Int

  /** host to bind to */
  def host: String

  val url = s"http://$host:$port"

  /** ChannelInitializer that initializes the server bootstrap */
  protected def initializer: ChannelInitializer[SocketChannel]

  /** EventLoopGroup associated with accepting client connections */
  protected def acceptorGroup: () => EventLoopGroup

  protected[this] lazy val boss = acceptorGroup()

  /** EventLoopGroup associated with handling client requests */
  protected def workerGroup: () => EventLoopGroup

  protected[this] lazy val workers = workerGroup()

  protected def houseKeepingEventExecutor: () => EventExecutor

  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup(
    "Netty Unfiltered Server Channel Group", houseKeepingEventExecutor())

  /** Starts default server bootstrap */
  def start() = start(identity)

  /** Starts server with preBind callback called before connection binding */
  def start(preBind: ServerBootstrap => ServerBootstrap): ServerBuilder = {
    val bootstrap = preBind(new ServerBootstrap()
      .group(boss, workers)
      .channel(classOf[NioServerSocketChannel])
      .childHandler(initializer)
      .childOption(ChannelOption.TCP_NODELAY, JBoolean.TRUE)
      .childOption(ChannelOption.SO_KEEPALIVE, JBoolean.TRUE)
      .option(ChannelOption.SO_RCVBUF, JInteger.valueOf(128 * 1024))
      .option(ChannelOption.SO_SNDBUF, JInteger.valueOf(128 * 1024))
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
    boss.shutdownGracefully()
    workers.shutdownGracefully()
    this
  }
}

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
trait DefaultServerInit {

  /** A ChannelGroup used to manage cleanup
   *  and channel closing on server shutdown in #closeConnections() */
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
         pl.addLast(s"handler-$idx", handler())
    }.addLast("notfound", new NotFoundHandler)
}

/**
 * Channel handler that keeps track of channels in a ChannelGroup for controlled
 * shutdown.
 */
@Sharable
class HouseKeepingChannelHandler(channels: ChannelGroup)
  extends ChannelInboundHandlerAdapter {
  override def channelActive(ctx: ChannelHandlerContext) = {
    // Channels are automatically removed from the group on close
    channels.add(ctx.channel)
    ctx.fireChannelActive()
  }
}

@Sharable
class NotFoundHandler
  extends ChannelInboundHandlerAdapter {
  override def channelRead(
    ctx: ChannelHandlerContext, msg: java.lang.Object): Unit =
    (msg match {
      case req: HttpMessage =>
        ReferenceCountUtil.release(req)
        Some(req.getProtocolVersion)
        // fixme(doug): this may no be unessessary
      case chunk: HttpContent =>
        ReferenceCountUtil.release(chunk)
        None
      case ue => sys.error("Unexpected message type from upstream: %s".format(ue))
    }).map { version =>
      ctx.channel.writeAndFlush(new DefaultHttpResponse(version, HttpResponseStatus.NOT_FOUND))
         .addListener(ChannelFutureListener.CLOSE)
    }.getOrElse(ctx.fireChannelRead(msg))
}
