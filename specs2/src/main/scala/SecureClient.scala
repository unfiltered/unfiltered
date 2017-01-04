package unfiltered.specs2

import okhttp3._
import java.security.KeyStore
import java.io.FileInputStream
import javax.net.ssl._

/** Provides an Http client configured to handle ssl certs */
trait SecureClient extends Hosted {
  val keyStorePath: String
  val keyStorePasswd: String
  val securePort: Int
  val secureScheme = "https"

  private def secure() = {
    val keys  = KeyStore.getInstance(KeyStore.getDefaultType)
    unfiltered.util.IO.use(new FileInputStream(keyStorePath)) { in =>
      keys.load(in, keyStorePasswd.toCharArray)
    }
    val trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm)
    trustManagerFactory.init(keys)
    val trustManagers = trustManagerFactory.getTrustManagers
    val trustManager = trustManagers.head.asInstanceOf[X509TrustManager]

    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, Array(trustManager), null)
    val sslSocketFactory = sslContext.getSocketFactory
    new OkHttpClient.Builder().sslSocketFactory(sslSocketFactory, trustManager)
  }


  def https(req: Request): Response = {
    val response = httpsx(req)
    if (response.code == 200) {
      response
    } else {
      throw StatusCode(response.code)
    }
  }

  def httpsx[T](req: Request): Response = {
    requestWithNewClient(req, secure())
  }
}
