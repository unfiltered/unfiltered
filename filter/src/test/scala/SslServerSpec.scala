package unfiltered.server

import unfiltered.spec
import org.specs._

object SslServerSpec extends Specification with spec.jetty.Served with spec.SecureClient {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.{Http => JHttp, Ssl}
  import org.apache.http.client.ClientProtocolException
  
  import dispatch._
  
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = 8443
  
  override val host = :/("localhost", securePort)
  
  override lazy val server = setup(new JHttp(port) with Ssl {
    def sslPort = securePort
    override lazy val keyStore = keyStorePath
    override lazy val keyStorePassword = keyStorePasswd
  })
  
  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/", _)) => ResponseString("secret") ~> Ok
  })}
  
  "A Secure Server" should {
    "respond to secure requests" in {
      http(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      http(host as_str) must throwA[ClientProtocolException]
    }
  }
}
