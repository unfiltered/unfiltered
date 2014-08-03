package unfiltered.netty

import unfiltered.util.IO

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.{ SslContext, SslHandler }
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.io.{ File, FileInputStream }
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext, SSLEngine }

/** A binder defines a port binding for a ServerBootstrap */
trait Binder {
  /** @return port to listen on */
  def port: Int
  /** contribute to a channel's initialization before defaults are applied */
  def init(channel: SocketChannel): SocketChannel
  /** bind to a ServerBootstrap returning the resulting ChannelFuture */
  def bind(bootstap: ServerBootstrap): ChannelFuture
}

object Binder {
  trait Simple extends Binder {
    def host: String
    def init(channel: SocketChannel) = channel
    def bind(boot: ServerBootstrap) =
      boot.bind(host, port)
  }

  trait Secure extends Binder {
    def host: String
    def handler(channel: SocketChannel): SslHandler
    def init(channel: SocketChannel) = {
      channel.pipeline.addLast(handler(channel))
      channel
    }
    def bind(boot: ServerBootstrap) =
      boot.bind(host, port)
  }
}

/** A mixin for binding Binders to an instance of a netty Server */
trait Binders {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443

  def bind(binder: Binder): Server

  def http(
    port: Int = defaultHttpPort,
    host: String = allInterfacesHost
  ) = bind(
    SocketBinder(port, host)
  )

  def local(port: Int) =
    http(port, localInterfaceHost)

  def anylocal =
    local(unfiltered.util.Port.any)

  def https(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslContextProvider
  ) = bind(
    SecureContextSocketBinder(port, host, ssl)
  )

  def httpsEngine(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslEngineProvider
  ) = bind(
    SecureEngineSocketBinder(port, host, ssl)
  )
}

/** A basic binder for socket addresses */
case class SocketBinder(port: Int, host: String) extends Binder.Simple

/** A binder for secure socket addresses backed by a netty SslContext */
case class SecureContextSocketBinder(
  port: Int,
  host: String,
  ssl: SslContextProvider
) extends Binder.Secure {
  def handler(channel: SocketChannel) =
    ssl.context.newHandler(channel.alloc)
}

/** A binder for secure socket addresses backed by
 *  an implementation of a SSLEngine */
case class SecureEngineSocketBinder(
  port: Int,
  host: String,
  ssl: SslEngineProvider
) extends Binder.Secure {
  def handler(channel: SocketChannel) =
    new SslHandler(ssl.engine)
}

trait SslEngineProvider {  
  def engine: SSLEngine
}

object SslEngineProvider {
  val defaultKeystorePathProperty = "netty.ssl.keyStore"
  val defaultKeystorePasswordProperty = "netty.ssl.keyStorePassword"

  /** An engine provider based on file system path to keystore */
  trait Path extends SslEngineProvider {
    def keyStorePath: String
    def keyStorePassword: String
    def engine = {
      val ctx = SSLContext.getInstance("TLS")
      ctx.init(keyManagers, null, new SecureRandom)
      val e = ctx.createSSLEngine
      e.setUseClientMode(false)
      e
    }

    private def keyManagers = {
      val password = keyStorePassword.toCharArray
      val keys = KeyStore.getInstance(
        System.getProperty(
          "netty.ssl.keyStoreType",
          KeyStore.getDefaultType
        )
      )
      IO.use(new FileInputStream(keyStorePath)) { in =>
        keys.load(in, password)
      }
      val factory = KeyManagerFactory.getInstance(
        System.getProperty(
          "netty.ssl.keyStoreAlgorithm",
          KeyManagerFactory.getDefaultAlgorithm
        )
      )
      factory.init(keys, password)
      factory.getKeyManagers
    }
  }

  object Path {
    case class Simple(
      keyStorePath: String,
      keyStorePassword: String
    ) extends Path

    case class SysProperties(
      keyStorePathProperty: String,
      keyStorePasswordProperty: String
    ) extends Path {
      private lazy val props = new sys.SystemProperties
      lazy val keyStorePath = props(keyStorePathProperty)
      lazy val keyStorePassword = props(keyStorePasswordProperty)
    }
  }

  def path(
    keyStorePath: String,
    keyStorePassword: String
  ) = Path.Simple(
    keyStorePath, keyStorePassword
  )

  def pathSysProperties(
    keyStorePathProperty: String = defaultKeystorePathProperty,
    keyStorePasswordProperty: String = defaultKeystorePasswordProperty
  ) = Path.SysProperties(
    keyStorePathProperty,
    keyStorePasswordProperty
  )
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
