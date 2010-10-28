package unfiltered.netty

import unfiltered.util.{IO, RunnableServer}
import java.util.concurrent.Executors
import org.jboss.netty.bootstrap.ServerBootstrap
import org.jboss.netty.channel.socket.nio.NioServerSocketChannelFactory
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel._
import group.{ChannelGroup, DefaultChannelGroup}
import unfiltered._

trait Server extends RunnableServer {
  val port: Int
  val host: String
  protected def pipelineFactory: ChannelPipelineFactory

  val DEFAULT_IO_THREADS = Runtime.getRuntime().availableProcessors() + 1;
  val DEFAULT_EVENT_THREADS = DEFAULT_IO_THREADS * 4;
  
  private var bootstrap: ServerBootstrap = _
  
  /** any channels added to this will receive broadcasted events */
  protected val channels = new DefaultChannelGroup("Netty Unfiltered Server Channel Group")

  def start(): this.type = {
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
    channels.add(bootstrap.bind(new InetSocketAddress(host, port)))
    this
  }
  
  def closeConnections(): this.type = {
    // Close any pending connections / channels (including server)
    channels.close.awaitUninterruptibly
    this
  }
  def destroy(): this.type = {
    // Release NIO resources to the OS
    bootstrap.releaseExternalResources
    this
  }
}

/** Mixin trait for Ssl support. 
  * For added truststore support, mix in the Trusted trait
  * note need to extend something with a 
  * concrete #pipelineFactory impl 
  * @note you may prefer to just use Https (less pipe twisting) */
trait Ssl extends Http with SslSecurity {
  import org.jboss.netty.handler.ssl.SslHandler
  
  lazy val newPipes = try { new ChannelPipelineFactory {
    def getPipeline(): ChannelPipeline = {
      val otherPipes = Ssl.super.pipelineFactory.getPipeline.toMap
      val line = Channels.pipeline
      val engine = createSslEngine
      engine.setUseClientMode(false)
      line.addLast("ssl", new SslHandler(engine))
      val jiter = otherPipes.entrySet.iterator
      while(jiter.hasNext) {
        val e = jiter.next
        line.addLast(e.getKey, e.getValue) 
      }
      line
    }
  } } catch { case e => 
    error("error securing pipeline %s" format e.getMessage)
  }
  
  /** prefixes channel pipeline with ssl handler */
  override def pipelineFactory = newPipes
  
  def createSslEngine = createSslContext.createSSLEngine
}

/** Provides security dependencies */
trait Security {
  import javax.net.ssl.SSLContext
  /** create an SSLContext from which an SSLEngine can be created */
  def createSslContext: SSLContext
}

/** Default implementation of Security provides basic ssl support. 
  * A keyStore, keyStorePassword are required and default to using the system property values
  * "jetty.ssl.keyStore" and "jetty.ssl.keyStorePassword" respectively.*/
trait SslSecurity extends Security {
  import java.io.FileInputStream
  
  import java.security.{KeyStore, SecureRandom}
  import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext}
  
  def requiredProperty(name: String) = System.getProperty(name) match {
    case null => error("required system property not set %s" format name)
    case prop => prop
  }
  
  lazy val keyStore = requiredProperty("netty.ssl.keyStore")
  lazy val keyStorePassword = requiredProperty("netty.ssl.keyStorePassword")
  
  def keyManagers = {
    val keys = KeyStore.getInstance(System.getProperty("netty.ssl.keyStoreType", KeyStore.getDefaultType))
    IO.use(new java.io.FileInputStream(keyStore)) { in =>
      keys.load(in, keyStorePassword.toCharArray)
    }
    val keyManFact = KeyManagerFactory.getInstance(System.getProperty("netty.ssl.keyStoreAlgorithm", KeyManagerFactory.getDefaultAlgorithm))
    keyManFact.init(keys, keyStorePassword.toCharArray)
    keyManFact.getKeyManagers
  }
  
  def createSslContext = {
    val context = SSLContext.getInstance("TLS")
    initSslContext(context)
    context
  }
  
  def initSslContext(ctx: SSLContext) = 
    ctx.init(keyManagers, null, new SecureRandom)
}

/** Mixin for SslSecurity which adds trust store security.
  * A trustStore and trustStorePassword are required and default 
  * to the System property values "netty.ssl.trustStore" and 
  * "netty.ssl.trustStorePassword" respectively
  */
trait Trusted { self: SslSecurity =>
  import java.io.FileInputStream
  import java.security.{KeyStore, SecureRandom}
  import javax.net.ssl.{SSLContext, TrustManager, TrustManagerFactory}
  
  lazy val trustStore = requiredProperty("netty.ssl.trustStore")
  lazy val trustStorePassword = requiredProperty("netty.ssl.trustStorePassword")
  
  def initSslContext(ctx: SSLContext) = 
    ctx.init(keyManagers, trustManagers, new SecureRandom)
  
  def trustManagers = {
    val trusts = KeyStore.getInstance(System.getProperty("netty.ssl.trustStoreType", KeyStore.getDefaultType))
    IO.use(new FileInputStream(trustStore)) { in =>
      trusts.load(in, trustStorePassword.toCharArray)
    }  
    val trustManFact = TrustManagerFactory.getInstance(System.getProperty("netty.ssl.trustStoreAlgorithm", TrustManagerFactory.getDefaultAlgorithm))
    trustManFact.init(trusts)
    trustManFact.getTrustManagers
  }
}

/** Default implementation of the Server trait. If you want to use a custom pipeline
 * factory it's better to extend Server directly. */
case class Http(port: Int, host: String,
                handlers: List[ChannelHandler],
                beforeStopBlock: () => Unit) extends Server with RunnableServer {
  def pipelineFactory: ChannelPipelineFactory = new ServerPipelineFactory(channels, handlers)
  
  def stop() = {
    beforeStopBlock()
    closeConnections()
    destroy()
  }
  def handler(h: ChannelHandler) = 
    Http(port, host, h :: handlers, beforeStopBlock)
  def beforeStop(block: => Unit) =
    Http(port, host, handlers, { () => beforeStopBlock(); block })
}

/** Http + Ssl implementation of the Server trait. */
case class Https(port: Int, host: String,
                handlers: List[ChannelHandler],
                beforeStopBlock: () => Unit) extends Server with RunnableServer with SslSecurity {
  def pipelineFactory: ChannelPipelineFactory = new SecureServerPipelineFactory(channels, handlers, this)
  
  def stop() = {
    beforeStopBlock()
    closeConnections()
    destroy()
  }
  def handler(h: ChannelHandler) = 
    Https(port, host, h :: handlers, beforeStopBlock)
  def beforeStop(block: => Unit) =
    Https(port, host, handlers, { () => beforeStopBlock(); block })
}

object Http {
  def apply(port: Int, host: String): Http = 
    Http(port, host, Nil, () => ())
  def apply(port: Int): Http =
    Http(port, "0.0.0.0")
}

object Https {
  def apply(port: Int, host: String): Https = 
    Https(port, host, Nil, () => ())
  def apply(port: Int): Https =
    Https(port, "0.0.0.0")
}

class ServerPipelineFactory(channels: ChannelGroup, handlers: List[ChannelHandler]) 
    extends ChannelPipelineFactory {
  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline

    line.addLast("housekeeping", new HouseKeepingChannelHandler(channels))
    line.addLast("decoder", new HttpRequestDecoder)
    line.addLast("encoder", new HttpResponseEncoder)
    handlers.reverse.foreach { h => line.addLast("handler", h) }

    line
  }
}

/** ChannelPipelineFactory for secure Http connections */
class SecureServerPipelineFactory(channels: ChannelGroup, handlers: List[ChannelHandler], security: Security) 
    extends ChannelPipelineFactory {
  import org.jboss.netty.handler.ssl.SslHandler
  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline
    
    val engine = security.createSslContext.createSSLEngine
    engine.setUseClientMode(false)
    line.addLast("ssl", new SslHandler(engine))
    line.addLast("housekeeping", new HouseKeepingChannelHandler(channels))
    line.addLast("decoder", new HttpRequestDecoder)
    line.addLast("encoder", new HttpResponseEncoder)
    handlers.reverse.foreach { h => line.addLast("handler", h) }

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
