package unfiltered.server

import unfiltered.spec
import org.specs._

object SslServerSpec extends Specification with spec.jetty.Served with spec.SecureClient {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.Https
  import unfiltered.util.Port
  import org.apache.http.client.ClientProtocolException

  import dispatch._

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  override val host = :/("localhost", securePort)

  override lazy val server = setup(new Https(securePort, "0.0.0.0") {
    override lazy val keyStore = keyStorePath
    override lazy val keyStorePassword = keyStorePasswd
  })

  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("secret") ~> Ok
  })}

  "A Secure Server" should {
    "respond to secure requests" in {
      https(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host as_str) must throwA[ClientProtocolException]
    }
  }
}
