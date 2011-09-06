package unfiltered.spec

import org.specs._
import dispatch._

/** Provides an Http client configured to handle ssl certs */
trait SecureClient {
  val keyStorePath: String
  val keyStorePasswd: String
  val securePort: Int
  val secureScheme = "https"

  def https[T](handler: => Handler[T]): T = {
    val h =  new Http {
      override def make_client = {
        import java.security.KeyStore
        import java.io.FileInputStream
        import org.apache.http.conn.ssl.SSLSocketFactory
        import org.apache.http.conn.scheme.Scheme

        val cli = super.make_client
        val keys  = KeyStore.getInstance(KeyStore.getDefaultType)
        unfiltered.util.IO.use(new FileInputStream(keyStorePath)) { in =>
          keys.load(in, keyStorePasswd.toCharArray)
        }
        cli.getConnectionManager.getSchemeRegistry.register(
          new Scheme(secureScheme, securePort, new SSLSocketFactory(keys))
        )
        cli
      }
    }
    try { h(handler) }
    finally { h.shutdown() }
  }
}
