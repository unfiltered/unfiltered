package unfiltered.netty

import unfiltered.util.{IO, RunnableServer}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{
  HttpRequestDecoder, HttpResponseEncoder, HttpChunkAggregator}
import org.jboss.netty.handler.stream.ChunkedWriteHandler
import org.jboss.netty.channel._
import group.ChannelGroup
import unfiltered._

object Https {
  def apply(port: Int, host: String): Https =
    Https(port, host, Nil, () => ())
  def apply(port: Int): Https =
    Https(port, "0.0.0.0")
   /** bind to a the loopback interface only */
  def local(port: Int): Https =
    Https(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(unfiltered.util.Port.any)
}

/** Http + Ssl implementation of the Server trait. */
case class Https(port: Int, host: String,
                 handlers: List[() => ChannelHandler],
                 beforeStopBlock: () => Unit)
extends HttpServer
with Ssl { self =>
  def pipelineFactory: ChannelPipelineFactory =
    new SecureServerPipelineFactory(channels, handlers, this)

  type ServerBuilder = Https

  def handler(h: ChannelHandler) = makePlan(h)

  def makePlan(h: => ChannelHandler) =
    Https(port, host, { () => h } :: handlers, beforeStopBlock)

  def beforeStop(block: => Unit) =
    Https(port, host, handlers, { () => beforeStopBlock(); block })
}

/** Provides security dependencies */
trait Security {
  import javax.net.ssl.SSLContext
  /** create an SSLContext from which an SSLEngine can be created */
  def createSslContext: SSLContext
}

/** Provides basic ssl support.
  * A keyStore and keyStorePassword are required and default to using the system property values
  * "jetty.ssl.keyStore" and "jetty.ssl.keyStorePassword" respectively. */
trait Ssl extends Security {
  import java.io.FileInputStream
  import java.security.{KeyStore, SecureRandom}
  import javax.net.ssl.{KeyManager, KeyManagerFactory, SSLContext}

  def requiredProperty(name: String) = System.getProperty(name) match {
    case null => error("required system property not set %s" format name)
    case prop => prop
  }

  lazy val keyStore = requiredProperty("netty.ssl.keyStore")
  lazy val keyStorePassword = requiredProperty("netty.ssl.keyStorePassword")

  lazy val keyManagers = {
    val keys = KeyStore.getInstance(System.getProperty(
      "netty.ssl.keyStoreType", KeyStore.getDefaultType))
    IO.use(new FileInputStream(keyStore)) { in =>
      keys.load(in, keyStorePassword.toCharArray)
    }
    val keyManFact = KeyManagerFactory.getInstance(System.getProperty(
      "netty.ssl.keyStoreAlgorithm", KeyManagerFactory.getDefaultAlgorithm))
    keyManFact.init(keys, keyStorePassword.toCharArray)
    keyManFact.getKeyManagers
  }

  lazy val createSslContext = {
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
trait Trusted { self: Ssl =>
  import java.io.FileInputStream
  import java.security.{KeyStore, SecureRandom}
  import javax.net.ssl.{SSLContext, TrustManager, TrustManagerFactory}

  lazy val trustStore = requiredProperty("netty.ssl.trustStore")
  lazy val trustStorePassword = requiredProperty("netty.ssl.trustStorePassword")

  def initSslContext(ctx: SSLContext) =
    ctx.init(keyManagers, trustManagers, new SecureRandom)

  def trustManagers = {
    val trusts = KeyStore.getInstance(System.getProperty(
      "netty.ssl.trustStoreType", KeyStore.getDefaultType))
    IO.use(new FileInputStream(trustStore)) { in =>
      trusts.load(in, trustStorePassword.toCharArray)
    }
    val trustManFact = TrustManagerFactory.getInstance(System.getProperty(
      "netty.ssl.trustStoreAlgorithm", TrustManagerFactory.getDefaultAlgorithm))
    trustManFact.init(trusts)
    trustManFact.getTrustManagers
  }
}

/** ChannelPipelineFactory for secure Http connections */
class SecureServerPipelineFactory(val channels: ChannelGroup,
                                  val handlers: List[() =>ChannelHandler],
                                  val security: Security)
    extends ChannelPipelineFactory with DefaultPipelineFactory {
  import org.jboss.netty.handler.ssl.SslHandler
  def getPipeline(): ChannelPipeline = {
    val line = Channels.pipeline

    val engine = security.createSslContext.createSSLEngine
    engine.setUseClientMode(false)
    line.addLast("ssl", new SslHandler(engine))
    complete(line)
  }
}
