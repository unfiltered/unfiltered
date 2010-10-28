package unfiltered.netty

import unfiltered.util.{IO, RunnableServer}
import java.net.InetSocketAddress
import org.jboss.netty.handler.codec.http.{HttpRequestDecoder, HttpResponseEncoder}
import org.jboss.netty.channel._
import group.ChannelGroup
import unfiltered._

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

object Https {
  def apply(port: Int, host: String): Https = 
    Https(port, host, Nil, () => ())
  def apply(port: Int): Https =
    Https(port, "0.0.0.0")
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