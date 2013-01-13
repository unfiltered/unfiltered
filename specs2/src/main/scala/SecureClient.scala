package unfiltered.spec

import dispatch.classic._
import org.apache.http.client.HttpClient

import java.security.KeyStore
import java.io.FileInputStream
import org.apache.http.conn.ssl.SSLSocketFactory
import org.apache.http.conn.scheme.Scheme

/** Provides an Http client configured to handle ssl certs */
trait SecureClient {
  val keyStorePath: String
  val keyStorePasswd: String
  val securePort: Int
  val secureScheme = "https"
  val logHttpsRequests = false

  /** Silent, resource-managed http request executor which accepts
   *  non-ok status */
  def xhttp[T](handler: Handler[T]): T  = {
    val h = if(logHttpsRequests) new Http else new Http with NoLogging
    try { h.x(handler) }
    finally { h.shutdown() }
  }

  private def secure(cli: HttpClient) = {
    val keys  = KeyStore.getInstance(KeyStore.getDefaultType)
    unfiltered.util.IO.use(new FileInputStream(keyStorePath)) { in =>
      keys.load(in, keyStorePasswd.toCharArray)
    }
    cli.getConnectionManager.getSchemeRegistry.register(
      new Scheme(secureScheme, securePort, new SSLSocketFactory(keys))
    )
    cli
  }

  /** Slient, resource-managed tls-enabled http request executor */
  def https[T](handler: => Handler[T]): T = {
    val h = if(logHttpsRequests) new Http {
      override def make_client =
        secure(super.make_client)
    } else new Http with NoLogging {
      override def make_client =
        secure(super.make_client)
    }
    try { h(handler) }
    finally { h.shutdown() }
  }
}
