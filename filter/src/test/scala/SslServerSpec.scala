package unfiltered.server

import org.specs._

object SslServerSpec extends Specification with unfiltered.spec.jetty.Served {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.{Http => JHttp, Ssl}
  import org.apache.http.client.ClientProtocolException
  
  import dispatch._
  
  override val host = :/("localhost", 8443)
  
  override lazy val server = setup(new JHttp(port) with Ssl {
    def sslPort = 8443
    override lazy val keyStore = getClass.getResource("/keystore").getPath
    override lazy val keyStorePassword = "unfiltered"
  })
  
  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/", _)) => ResponseString("secret") ~> Ok
  })}
  
  "A Secure Server" should {
    "respond to secure requests" in {
      Http(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      Http(host as_str) must throwA[ClientProtocolException]
    }
  }
}
