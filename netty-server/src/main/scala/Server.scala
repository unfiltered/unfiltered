package unfiltered.netty

import unfiltered.util.PlanServer
import unfiltered.util.RunnableServer
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelHandler
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.WriteBufferWaterMark
import io.netty.channel.epoll.Epoll
import io.netty.channel.epoll.EpollServerSocketChannel
import io.netty.channel.kqueue.KQueue
import io.netty.channel.kqueue.KQueueServerSocketChannel
import io.netty.channel.socket.ServerSocketChannel
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.HttpRequestDecoder
import io.netty.handler.codec.http.HttpResponseEncoder
import io.netty.handler.stream.ChunkedWriteHandler
import java.lang.{Boolean => JBoolean}
import java.lang.{Integer => JInteger}
import java.net.URL

object Server extends PortBindings {
  def bind(binding: PortBinding): Server =
    Server(binding :: Nil, Nil, () => (), 1048576, Engine.Default)
}

/** A RunnableServer backed by a list of netty bootstrapped port bindings
 *  @param portBindings a list of port bindings
 *  @param handlers a list of functions which produce channel handlers
 *  @param beforeStopBlock a function to be invoked when the server is shutdown before channels are closed
 *  @param chunkSize the maximum size allowed for request body chunks
 *  @param engine defines a set of resourced used to make a netty server run */
case class Server(
  portBindings: List[PortBinding],
  handlers: List[() => ChannelHandler],
  beforeStopBlock: () => Unit,
  chunkSize: Int,
  engine: Engine
) extends RunnableServer
    with PlanServer[ChannelHandler]
    with PortBindings
    with Engine.Builder[Server] {
  type ServerBuilder = Server

  private[this] lazy val acceptorGrp = engine.acceptor
  private[this] lazy val workerGrp = engine.workers
  private[this] lazy val channelGrp = engine.channels

  def use(engine: Engine): Server =
    copy(engine = engine)

  def bind(binding: PortBinding): Server =
    copy(portBindings = binding :: portBindings)

  def ports: Iterable[Int] = portBindings.map(_.port)

  def makePlan(plan: => ChannelHandler): ServerBuilder =
    copy(handlers = { () => plan } :: handlers)

  def handler(h: ChannelHandler): ServerBuilder = makePlan(h)

  /** Starts server in the background */
  def start(): ServerBuilder = start(identity)

  /** Starts server in the background after applying a function
   *  to each port bindings server bootstrap */
  def start(prebind: ServerBootstrap => ServerBootstrap): Server = {
    val channelClz: Class[? <: ServerSocketChannel] = if (Epoll.isAvailable) {
      classOf[EpollServerSocketChannel]
    } else if (KQueue.isAvailable) {
      classOf[KQueueServerSocketChannel]
    } else {
      classOf[NioServerSocketChannel]
    }
    val bindings = portBindings.map { binding =>
      val bootstrap = configure(
        new ServerBootstrap().group(acceptorGrp, workerGrp).channel(channelClz).childHandler(initializer(binding))
      )
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
  def stop(): ServerBuilder = {
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
  def destroy(): ServerBuilder = {
    workerGrp.shutdownGracefully()
    acceptorGrp.shutdownGracefully()
    this
  }

  def closeConnections(): Server = {
    channelGrp.close.awaitUninterruptibly()
    this
  }

  def beforeStop(block: => Unit): Server =
    copy(beforeStopBlock = { () => beforeStopBlock(); block })

  def configure(bootstrap: ServerBootstrap): ServerBootstrap =
    bootstrap
      .childOption(ChannelOption.TCP_NODELAY, JBoolean.TRUE)
      /* http://normanmaurer.me/presentations/2014-facebook-eng-netty/slides.html#11.0 */
      .childOption(ChannelOption.WRITE_BUFFER_WATER_MARK, new WriteBufferWaterMark(8 * 1024, 32 * 1024))
      .childOption(ChannelOption.SO_KEEPALIVE, JBoolean.TRUE)
      .childOption(ChannelOption.SO_RCVBUF, JInteger.valueOf(128 * 1024))
      .childOption(ChannelOption.SO_SNDBUF, JInteger.valueOf(128 * 1024))
      .option(ChannelOption.SO_REUSEADDR, JBoolean.TRUE)
      .option(ChannelOption.SO_BACKLOG, JInteger.valueOf(16384))

  /** @param binding a binder which may contribute to channel initialization */
  def initializer(binding: PortBinding): ChannelInitializer[SocketChannel] =
    (channel: SocketChannel) =>
      handlers.reverse.zipWithIndex
        .foldLeft(
          binding
            .init(channel)
            .pipeline
            .addLast("housekeeper", new HouseKeeper(channelGrp))
            .addLast("decoder", new HttpRequestDecoder)
            .addLast("encoder", new HttpResponseEncoder)
            .addLast("chunker", new HttpObjectAggregator(chunkSize))
        ) { case (pipe, (handler, index)) =>
          pipe.addLast(s"handler-$index", handler())
        }
        .addLast("notfound", new NotFoundHandler)

  def chunked(size: Int): Server = copy(chunkSize = size)

  def resources(path: URL, cacheSeconds: Int = 60, passOnFail: Boolean = true): Server = {
    val resources = Resources(path, cacheSeconds, passOnFail)
    this.makePlan(new ChunkedWriteHandler).plan(resources)
  }
}
