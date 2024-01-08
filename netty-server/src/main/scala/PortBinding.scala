package unfiltered.netty

import unfiltered.util.HttpPortBinding
import unfiltered.util.HttpsPortBinding
import unfiltered.util.IO
import unfiltered.util.Port
import unfiltered.util.PortBindingInfo
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.socket.SocketChannel
import io.netty.handler.ssl.SslContext
import io.netty.handler.ssl.SslContextBuilder
import io.netty.handler.ssl.SslProvider
import io.netty.handler.ssl.SslHandler
import io.netty.handler.ssl.util.SelfSignedCertificate
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import io.netty.channel.ChannelFuture

/** A PortBinding defines a binding for a ServerBootstrap for a given address  and port */
trait PortBinding extends PortBindingInfo {

  /** contribute to a channel's initialization before defaults are applied */
  def init(channel: SocketChannel): SocketChannel
}

object PortBinding {
  trait Simple extends PortBinding with HttpPortBinding {
    def init(channel: SocketChannel) = channel
  }

  trait Secure extends PortBinding with HttpsPortBinding {
    def handler(channel: SocketChannel): SslHandler
    def init(channel: SocketChannel): SocketChannel = {
      channel.pipeline.addLast(handler(channel))
      channel
    }
    def bind(boot: ServerBootstrap): ChannelFuture =
      boot.bind(host, port)
  }
}

/** A mixin for binding ports to an instance of a netty Server */
trait PortBindings {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443

  def bind(binding: PortBinding): Server

  def http(
    port: Int = defaultHttpPort,
    host: String = allInterfacesHost
  ): Server = bind(
    SocketBinding(port, host)
  )

  def local(port: Int): Server =
    http(port, localInterfaceHost)

  def anylocal: Server =
    local(Port.any)

  def https(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslContextProvider
  ): Server = bind(
    SecureContextSocketBinding(port, host, ssl)
  )

  def httpsEngine(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    ssl: SslEngineProvider
  ): Server = bind(
    SecureEngineSocketBinding(port, host, ssl)
  )
}

/** A basic port binding for a socket addresses */
case class SocketBinding(port: Int, host: String) extends PortBinding.Simple

/** A port binding for secure socket addresses backed by a netty SslContext */
case class SecureContextSocketBinding(
  port: Int,
  host: String,
  ssl: SslContextProvider
) extends PortBinding.Secure {
  def handler(channel: SocketChannel): SslHandler =
    ssl.context.newHandler(channel.alloc)
}

/** A port binding for secure socket addresses backed by
 *  an implementation of a SSLEngine */
case class SecureEngineSocketBinding(
  port: Int,
  host: String,
  ssl: SslEngineProvider
) extends PortBinding.Secure {
  def handler(channel: SocketChannel) =
    new SslHandler(ssl.engine)
}

trait SslEngineProvider {
  def engine: SSLEngine
}

object SslEngineProvider {
  val defaultKeystorePathProperty = "netty.ssl.keyStore"
  val defaultKeystorePasswordProperty = "netty.ssl.keyStorePassword"

  case class Simple(engine: SSLEngine) extends SslEngineProvider

  /** An engine provider based on file system path to keystore */
  trait Path extends SslEngineProvider {
    def keyStorePath: String
    def keyStorePassword: String
    def engine: SSLEngine = {
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
      lazy val keyStorePath: String = props(keyStorePathProperty)
      lazy val keyStorePassword: String = props(keyStorePasswordProperty)
    }
  }

  def apply(engine: SSLEngine): Simple = Simple(engine)

  def path(
    keyStorePath: String,
    keyStorePassword: String
  ): Path.Simple = Path.Simple(
    keyStorePath,
    keyStorePassword
  )

  def pathSysProperties(
    keyStorePathProperty: String = defaultKeystorePathProperty,
    keyStorePasswordProperty: String = defaultKeystorePasswordProperty
  ): Path.SysProperties = Path.SysProperties(
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
  ): SslContextProvider = new SslContextProvider {
    def context = SslContextBuilder.forServer(cert.certificate(), cert.privateKey()).build
  }
  def keys(
    certChain: File,
    key: File,
    password: Option[String] = None,
    nettyProvider: Option[SslProvider] = None
  ): SslContextProvider = new SslContextProvider {
    def context = SslContextBuilder.forServer(certChain, key, password.orNull).sslProvider(nettyProvider.orNull).build
  }
}
