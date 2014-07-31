package unfiltered.netty.refactor

import unfiltered.netty.{ HouseKeepingChannelHandler, NotFoundHandler, Resources }
import unfiltered.util.{ IO, PlanServer, RunnableServer }

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.{
  ChannelFuture,
  ChannelHandler, ChannelInitializer,
  ChannelOption
}
import io.netty.channel.group.DefaultChannelGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.codec.http.{
  HttpObjectAggregator,
  HttpRequestDecoder,
  HttpResponseEncoder
}
import io.netty.handler.ssl.{ SslContext, SslHandler }
import io.netty.handler.ssl.util.SelfSignedCertificate
import io.netty.handler.stream.ChunkedWriteHandler
import io.netty.util.concurrent.GlobalEventExecutor
import java.lang.{ Boolean => JBoolean, Integer => JInteger }
import java.io.{ File, FileInputStream }
import java.net.URL
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManager, KeyManagerFactory, SSLContext, SSLEngine }

object Http extends Binders {  
  def bind(binder: Binder): Http =
    Http(binder :: Nil, Nil, () => (), 1048576)
}

/** A RunnableServer backed by a list of netty bootstraped port bindings
 *  @param binders a list of port bindings
 *  @param handlers a list of functions which produce channel handlers
 *  @param beforeStopBlock a function to be invoked when the server is shutdown before channels are closed
 *  @param chunkSize the maximum size allowed for request body chunks */
case class Http(
  binders: List[Binder],
  handlers: List[() => ChannelHandler],
  beforeStopBlock: () => Unit,
  chunkSize: Int
) extends RunnableServer
  with PlanServer[ChannelHandler]
  with Binders {
  type ServerBuilder = Http

  /** a shared event loop group for accepting connections shared between bootraps */
  private[this] lazy val acceptor  = new NioEventLoopGroup()

  /** a shared event loop group for handling accepted connections shared between bootraps */
  private[this] lazy val workers   = new NioEventLoopGroup()

  /** a channel group used to collect connected channels so that they may be shutdown propertly on RunnableServer#stop() */
  lazy val channels = new DefaultChannelGroup(
    "Netty Unfiltered Server Channel Group",
    GlobalEventExecutor.INSTANCE) // todo: pick the best eventLoop option for the task

  def chunked(size: Int) = copy(chunkSize = size)

  override def bind(binder: Binder) = copy(binders = binder :: binders)

  override def ports = binders.map(_.port)

  def resources(
    path: URL,
    cacheSeconds: Int   = 60,
    passOnFail: Boolean = true) = {
    val resources = Resources(path, cacheSeconds, passOnFail)
    this.plan(resources).makePlan(new ChunkedWriteHandler)
  }

  override def makePlan(plan: => ChannelHandler) =
    copy(handlers = { () => plan } :: handlers)

  override def start() = start(identity)

  def start(prebind: ServerBootstrap => ServerBootstrap) = {    
    val bindings = binders.map { binder =>
      val bootstrap = prebind(
        configure(new ServerBootstrap()
                  .group(acceptor, workers)
                  .channel(classOf[NioServerSocketChannel])
                  .childHandler(initializer(binder))))
      binder.bind(bootstrap).sync
    }
    bindings.foreach(b => channels.add(b.channel))

    this
  }

  override def stop() = {
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

  override def destroy() = {
    workers.shutdownGracefully()
    acceptor.shutdownGracefully()
    this
  }

  def closeConnections() = {
    channels.close.awaitUninterruptibly()
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
  def initializer(binder: Binder): ChannelInitializer[SocketChannel] =
    new ChannelInitializer[SocketChannel] {
      def initChannel(channel: SocketChannel) = {
        binder.init(channel)
        (channel.pipeline
          .addLast("housekeeping", new HouseKeepingChannelHandler(channels))
          .addLast("decoder", new HttpRequestDecoder)
          .addLast("encoder", new HttpResponseEncoder)
          .addLast("chunker", new HttpObjectAggregator(chunkSize)) /: handlers.reverse.zipWithIndex) {
            case (pipe, (handler, index)) =>
              pipe.addLast(s"handler-$index", handler())
        }.addLast("notfound", new NotFoundHandler)
      }
    }
}

/** A binder defines a port binding for a ServerBootstrap. */
trait Binder {
  /** @return port to listen on */
  def port: Int
  /** contribute to a channel's initialization */
  def init(channel: SocketChannel): SocketChannel
  /** bind to a ServerBootstrap returning the resulting ChannelFuture */
  def bind(bootstap: ServerBootstrap): ChannelFuture
}

/** A mixin for binding Binders to an instance of Http */
trait Binders {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443

  def bind(binder: Binder): Http

  def apply(port: Int = defaultHttpPort, host: String = allInterfacesHost) =
    bind(SocketBinder(port, host))

  def local(port: Int) =
    apply(port, localInterfaceHost)

  def anylocal: Http =
    local(unfiltered.util.Port.any)

  def secure(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslContextProvider) =
    bind(SecureContextSocketBinder(port, host, ssl))

  def secureEngine(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslEngineProvider) =
    bind(SecureEngineSocketBinder(port, host, ssl))
}

/** A basic binder for socket addreses */
case class SocketBinder(
  port: Int, host: String) extends Binder {
  def init(channel: SocketChannel) = channel
  def bind(boot: ServerBootstrap) =
    boot.bind(host, port)
}

/** A binder for secure socket addresses backed by a netty SslContext */
case class SecureContextSocketBinder(
  port: Int, host: String, ssl: SslContextProvider) extends Binder {
  def init(channel: SocketChannel) = {
    channel.pipeline.addLast(ssl.context.newHandler(channel.alloc))
    channel
  }
  def bind(boot: ServerBootstrap) =
    boot.bind(host, port)
}

/** A binder for secure socket addresses backed by
 *  an implementation of a SSLEngine */
case class SecureEngineSocketBinder(
  port: Int, host: String, ssl: SslEngineProvider) extends Binder {
  def init(channel: SocketChannel) = {
    channel.pipeline.addLast(new SslHandler(ssl.engine))
    channel
  }
  def bind(boot: ServerBootstrap) =
    boot.bind(host, port)
}

trait SslEngineProvider {  
  def engine: SSLEngine
}

/** An engine provider based on file system paths */
trait SslEngineFromPath extends SslEngineProvider {
  def keyStorePath: String
  def keyStorePassword: String
  def engine = {
    val ctx = SSLContext.getInstance("TLS")
    ctx.init(keyManagers, null, new SecureRandom)
    ctx.createSSLEngine
  }

  private def keyManagers = {
    val password = keyStorePassword.toCharArray
    val keys = KeyStore.getInstance(
      System.getProperty(
        "netty.ssl.keyStoreType",
        KeyStore.getDefaultType))
    IO.use(new FileInputStream(keyStorePath)) { in =>
      keys.load(in, password)
    }
    val factory = KeyManagerFactory.getInstance(
      System.getProperty(
        "netty.ssl.keyStoreAlgorithm",
        KeyManagerFactory.getDefaultAlgorithm))
    factory.init(keys, password)
    factory.getKeyManagers
  }
}

object SslEngineFromPath {
  case class Simple(
    keyStorePath: String,
    keyStorePassword: String
  ) extends SslEngineFromPath

  case class Properties(
    keyStorePathProperty: String,
    keyStorePasswordProperty: String
  ) extends SslEngineFromPath {
    private lazy val props = new sys.SystemProperties
    lazy val keyStorePath = props(keyStorePathProperty)
    lazy val keyStorePassword = props(keyStorePasswordProperty)
  }
}

trait SslContextProvider {
  def context: SslContext
}

object SslContextProvider {
  def selfSigned(
    cert: SelfSignedCertificate
  ) = new SslContextProvider {
    def context = SslContext.newServerContext(
      cert.certificate(), cert.privateKey())
  }
  def keys(
    certChain: File,
    key: File,
    password: Option[String] = None
  ) = new SslContextProvider {
    def context = SslContext.newServerContext(
      certChain, key, password.orNull)
  }
}
