package unfiltered.jetty.refactor

import org.eclipse.jetty.server.{Server => JettyServer, Connector, Handler}
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.server.ssl.SslSocketConnector
import org.eclipse.jetty.util.ssl.SslContextFactory

/** Holds connector providers that listen to selected ports and interfaces.
  * HttpBuilder provides convenience methods for attaching connectors. */
case class Http(
  connectorProviders: List[ConnectorProvider]
) extends unfiltered.util.RunnableServer with HttpBuilder { self =>
  type ServerBuilder = Http

  def attach(connector: ConnectorProvider) = copy(connector :: connectorProviders)

  lazy val underlying = {
    val server = new JettyServer()
    for (provider <- connectorProviders)
      server.addConnector(provider.connector)
    server
  }
  lazy val port = connectorProviders.headOption.map(_.port).getOrElse(0)
  /** Starts server in the background */
  def start() = {
    underlying.setStopAtShutdown(true)
    underlying.start()
    this
  }
  /** Stops server running in the background */
  def stop() = {
    underlying.stop()
    this
  }
  /** Destroys the Jetty server instance and frees its resources.
   * Call after stopping a server, if finished with the instance,
   * to help avoid PermGen errors in an ongoing JVM session. */
  def destroy() = {
    underlying.destroy()
    this
  }
}

/** Base object that used to construct Http instances.
  * HttpBuilder provides convenience methods for attaching connectors. */
object Http extends HttpBuilder {
  def attach(connector: ConnectorProvider) = Http(connector :: Nil)
}

/** Convenience methods for attaching connector providers. */
trait HttpBuilder {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443
  val defaultKeystorePathProperty = "jetty.ssl.keyStore"
  val defaultKeystorePasswordProperty = "jetty.ssl.keyStorePassword"

  def attach(connector: ConnectorProvider): Http

  def apply(port: Int = defaultHttpPort, host: String = allInterfacesHost) = attach(
    SocketConnectorProvider(port, host)
  )

  def local(port: Int): Http = attach(
    SocketConnectorProvider(port, localInterfaceHost)
  )

  def anylocal: Http = local(unfiltered.util.Port.any)

  def secure(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePath: String,
    keyStorePassword: String
  ) = attach(
    SslSocketConnectorProvider(
      port,
      host,
      SslContextProvider.Simple(
        keyStorePath = keyStorePath,
        keyStorePassword = keyStorePassword
      )
    )
  )

  def secureSysProperties(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePathProperty: String = defaultKeystorePathProperty,
    keyStorePasswordProperty: String = defaultKeystorePasswordProperty
  ) = attach(
    SslSocketConnectorProvider(
      port,
      host,
      PropertySslContextProvider(
        keyStorePathProperty = keyStorePathProperty,
        keyStorePasswordProperty = keyStorePasswordProperty
      )
    )
  )
}

trait ConnectorProvider {
  def connector: Connector
  def port: Int
  def host: String
}

case class SocketConnectorProvider (
  port: Int,
  host: String
) extends ConnectorProvider {
  lazy val connector = {
    val c = new SocketConnector
    c.setPort(port)
    c.setHost(host)
    c
  }
}

trait SslContextProvider {
  def keyStorePath: String
  def keyStorePassword: String
  lazy val sslContextFactory = {
    val factory = new SslContextFactory
    factory.setKeyStorePath(keyStorePath)
    factory.setKeyStorePassword(keyStorePassword)
    factory
  }
}

object SslContextProvider {
  case class Simple(
    keyStorePath: String,
    keyStorePassword: String
  ) extends SslContextProvider
}

case class PropertySslContextProvider(
  keyStorePathProperty: String,
  keyStorePasswordProperty: String
) extends SslContextProvider {
  private lazy val props = new sys.SystemProperties
  lazy val keyStorePath = props(keyStorePathProperty)
  lazy val keyStorePassword = props(keyStorePasswordProperty)
}

case class SslSocketConnectorProvider (
  port: Int,
  host: String,
  sslContextProvider: SslContextProvider
) extends ConnectorProvider {

  lazy val connector = {
    val c = new SslSocketConnector(sslContextProvider.sslContextFactory)
    c.setPort(port)
    c.setHost(host)
    c
  }
}
