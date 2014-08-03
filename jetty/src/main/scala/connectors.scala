package unfiltered.jetty

import org.eclipse.jetty.server.Connector
import org.eclipse.jetty.server.bio.SocketConnector
import org.eclipse.jetty.server.ssl.SslSocketConnector
import org.eclipse.jetty.util.ssl.SslContextFactory

/** Convenience methods for adding connector providers. */
trait ConnectorBuilder {
  val allInterfacesHost = "0.0.0.0"
  val localInterfaceHost = "127.0.0.1"
  val defaultHttpPort = 80
  val defaultHttpsPort = 443
  val defaultKeystorePathProperty = "jetty.ssl.keyStore"
  val defaultKeystorePasswordProperty = "jetty.ssl.keyStorePassword"

  def connector(connector: ConnectorProvider): Server

  def http(port: Int = defaultHttpPort, host: String = allInterfacesHost) =
    connector(SocketConnectorProvider(port, host))

  def local(port: Int): Server =
    http(port, localInterfaceHost)

  def anylocal: Server = local(unfiltered.util.Port.any)

  def https(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePath: String,
    keyStorePassword: String
  ) = connector(
    SslSocketConnectorProvider(
      port,
      host,
      SslContextProvider.Simple(
        keyStorePath = keyStorePath,
        keyStorePassword = keyStorePassword
      )
    )
  )

  def httpsSysProperties(
    port: Int = defaultHttpsPort,
    host: String = allInterfacesHost,
    keyStorePathProperty: String = defaultKeystorePathProperty,
    keyStorePasswordProperty: String = defaultKeystorePasswordProperty
  ) = connector(
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
