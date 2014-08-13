package unfiltered.jetty

import unfiltered.util.{ HttpsPortBindingShim, Port }
import org.eclipse.jetty.server.ssl.SslSocketConnector
import org.eclipse.jetty.util.ssl.SslContextFactory

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
object Https {
  /** bind to the given port for any host */
  def apply(port: Int): Https = Https(port, "0.0.0.0")
  /** bind to a the loopback interface only */
  def local(port: Int) = Https(port, "127.0.0.1")
  /** bind to any available port on the loopback interface */
  def anylocal = local(Port.any)
}

@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
case class Https(port: Int, host: String) extends JettyBase with Ssl {
  type ServerBuilder = Https
  val url = "https://%s:%d/" format (host, port)
  def sslPort = port
  sslConn.setHost(host)
  def portBindings = HttpsPortBindingShim(host, port) :: Nil
}

/** Provides ssl support for Servers. This trait only requires a x509 keystore cert.
  * A keyStore, keyStorePassword are required and default to using the system property values
  * "jetty.ssl.keyStore" and "jetty.ssl.keyStorePassword" respectively.
  * For added trust store support, mix in the Trusted trait */
@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
trait Ssl { self: JettyBase =>

  def tryProperty(name: String) = System.getProperty(name) match {
    case null => sys.error("required system property not set %s" format name)
    case prop => prop
  }

  def sslPort: Int
  val sslMaxIdleTime = 90000
  val sslHandshakeTimeout = 120000
  lazy val keyStore = tryProperty("jetty.ssl.keyStore")
  lazy val keyStorePassword = tryProperty("jetty.ssl.keyStorePassword")

  val sslContextFactory = new SslContextFactory {
      setKeyStorePath(keyStore)
      setKeyStorePassword(keyStorePassword)
  }
  val sslConn = new SslSocketConnector(sslContextFactory) {
    setPort(sslPort)
    setMaxIdleTime(sslMaxIdleTime)
    setHandshakeTimeout(sslHandshakeTimeout)
  }
  underlying.addConnector(sslConn)
}

/** Provides truststore support to an Ssl supported Server
  * A trustStore and trustStorePassword are required and default
  * to the System property values "jetty.ssl.trustStore" and
  * "jetty.ssl.trustStorePassword" respectively */
@deprecated("Use unfiltered.jetty.Server", since="0.8.1")
trait Trusted { self: Ssl =>
  lazy val trustStore = tryProperty("jetty.ssl.trustStore")
  lazy val trustStorePassword = tryProperty("jetty.ssl.trustStorePassword")
  sslContextFactory.setTrustStore(trustStore)
  sslContextFactory.setTrustStorePassword(trustStorePassword)
}
