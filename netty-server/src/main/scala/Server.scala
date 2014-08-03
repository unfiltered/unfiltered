package unfiltered.netty

import unfiltered.util.{ PlanServer, RunnableServer }

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{ ChannelHandler, ChannelInitializer, ChannelOption, EventLoopGroup }
import io.netty.channel.group.{ ChannelGroup, DefaultChannelGroup }
import io.netty.channel.nio.{ NioEventLoop, NioEventLoopGroup }
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  HttpObjectAggregator,
  HttpRequestDecoder,
  HttpResponseEncoder
}
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.ImmediateEventExecutor
import java.lang.{ Boolean => JBoolean, Integer => JInteger }

import java.net.URL

object Server extends PortBindings {  
  def bind(binding: PortBinding): Server =
    Server(binding :: Nil, Nil, () => (), 1048576, Engine.Default)
}

/** Defines the set of resources used for process scheduling
 *  and collecting active channels needed for graceful shutdown */
trait Engine {
  /** a shared event loop group for accepting connections shared between bootstraps */
  def acceptor: EventLoopGroup
  /** a shared event loop group for handling accepted connections shared between bootstraps */
  def workers: EventLoopGroup
  /** a channel group used to collect active channels so that they may be shutdown properly on RunnableServer#stop() */
  def channels: ChannelGroup
}

object Engine {
  object Default extends Engine {
    def acceptor = new NioEventLoopGroup()
    def workers = new NioEventLoopGroup()
    def channels = new DefaultChannelGroup(
      "Netty Unfiltered Server Channel Group",
      ImmediateEventExecutor.INSTANCE)
  }
}

/** A RunnableServer backed by a list of netty bootstrapped port bindings
 *  @param portBindings a list of port bindings
 *  @param handlers a list of functions which produce channel handlers
 *  @param beforeStopBlock a function to be invoked when the server is shutdown before channels are closed
 *  @param chunkSize the maximum size allowed for request body chunks */
case class Server(
  portBindings: List[PortBinding],
  handlers: List[() => ChannelHandler],
  beforeStopBlock: () => Unit,
  chunkSize: Int,
  engine: Engine
) extends RunnableServer
  with PlanServer[ChannelHandler]
  with PortBindings {
  type ServerBuilder = Server

  private[this] lazy val acceptorGrp = engine.acceptor
  private[this] lazy val workerGrp  = engine.workers
  private[this] lazy val channelGrp = engine.channels

  /** Specifies the EventLoopGroup use to handle incoming connections */
  def acceptor(group: EventLoopGroup) = copy(engine = new Engine {
      lazy val acceptor = group
      lazy val workers = engine.workers
      lazy val channels = engine.channels
  })

  /** Specifies the EventLoopGroup use to handle processing of registered channels */
  def workers(group: EventLoopGroup) = copy(engine = new Engine {
    lazy val acceptor = engine.acceptor
    lazy val workers = group
    lazy val channels = engine.channels
  })

  /** Specifies the ChannelGroup used for collecting connected channels */
  def channels(group: ChannelGroup) = copy(engine = new Engine {
    lazy val acceptor = engine.acceptor
    lazy val workers = engine.workers
    lazy val channels = group
  })

  def bind(binding: PortBinding) =
    copy(portBindings = binding :: portBindings)

  def ports: Traversable[Int] = portBindings.map(_.port)

  def makePlan(plan: => ChannelHandler) =
    copy(handlers = { () => plan } :: handlers)

  def handler(h: ChannelHandler) = makePlan(h)

  /** Starts server in the background */
  def start() = start(identity)

  /** Starts server in the background after applying a function
   *  to each port bindings server bootstrap */
  def start(prebind: ServerBootstrap => ServerBootstrap) = {    
    val bindings = portBindings.map { binding =>
      val bootstrap = configure(
        new ServerBootstrap()
          .group(acceptorGrp, workerGrp)
          .channel(classOf[NioServerSocketChannel])
          .childHandler(initializer(binding)))
      prebind(bootstrap).bind(binding.host, binding.port).sync
    }
    for (binding <- bindings)
      channelGrp.add(binding.channel)

    this
  }

  /** Stops server running the background. If provided,
   *  the beforeStop block will be invoked before
   *  closing channel connections. Any listed cycle plans
   *  will be shutdown in the order provided. Lastly
   *  shared thread resources will be released. */
  def stop() = {
    beforeStopBlock()
    closeConnections()
    for (handler <- handlers) handler() match {
      case p: unfiltered.netty.cycle.Plan => p.shutdown()
      case _ => ()
    }
    destroy()
  }

  /** Destroys the provided worker event loop group
   *  before destroying the acceptors event loop group */
  def destroy() = {
    workerGrp.shutdownGracefully()
    acceptorGrp.shutdownGracefully()
    this
  }

  def closeConnections() = {
    channelGrp.close.awaitUninterruptibly()
    this
  }

  def beforeStop(block: => Unit) =
    copy(beforeStopBlock = { () => beforeStopBlock(); block })

  def configure(bootstrap: ServerBootstrap): ServerBootstrap =
     bootstrap.childOption(ChannelOption.TCP_NODELAY, JBoolean.TRUE)     
      /* http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#11.0 */
      .childOption(ChannelOption.WRITE_BUFFER_HIGH_WATER_MARK, JInteger.valueOf(32 * 1024))
      .childOption(ChannelOption.WRITE_BUFFER_LOW_WATER_MARK, JInteger.valueOf(8 * 1024))
      .childOption(ChannelOption.SO_KEEPALIVE, JBoolean.TRUE)
      .option(ChannelOption.SO_RCVBUF, JInteger.valueOf(128 * 1024))
      .option(ChannelOption.SO_SNDBUF, JInteger.valueOf(128 * 1024))
      .option(ChannelOption.SO_REUSEADDR, JBoolean.TRUE)
      .option(ChannelOption.SO_BACKLOG, JInteger.valueOf(16384))

  /** @param binder a binder which may contribute to channel initialization */
  def initializer(binding: PortBinding): ChannelInitializer[SocketChannel] =
    new ChannelInitializer[SocketChannel] {
      def initChannel(channel: SocketChannel) =
        (binding.init(channel).pipeline
          .addLast("housekeeper", new HouseKeeper(channelGrp))
          .addLast("decoder", new HttpRequestDecoder)
          .addLast("encoder", new HttpResponseEncoder)
          .addLast("chunker", new HttpObjectAggregator(chunkSize))
           /: handlers.reverse.zipWithIndex) {
            case (pipe, (handler, index)) =>
              pipe.addLast(s"handler-$index", handler())
          }.addLast("notfound", new NotFoundHandler)
    }

  def chunked(size: Int) = copy(chunkSize = size)

  def resources(
    path: URL,
    cacheSeconds: Int   = 60,
    passOnFail: Boolean = true) = {
    val resources = Resources(path, cacheSeconds, passOnFail)
    this.plan(resources).makePlan(new ChunkedWriteHandler)
  }
}
