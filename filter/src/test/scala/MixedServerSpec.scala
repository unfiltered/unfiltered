package unfiltered.server

import unfiltered.spec
import org.specs._

object MixedServerSpec extends Specification with spec.jetty.Served with spec.SecureClient {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.{Http, Ssl}
  import org.apache.http.client.ClientProtocolException
  
  import dispatch._
  
  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = 8443
  
  override val host = :/("localhost", 8080)
  
  override lazy val server = setup(new Http(8080, "0.0.0.0") with Ssl {
    def sslPort = securePort
    override lazy val keyStore = keyStorePath
    override lazy val keyStorePassword = keyStorePasswd
  })
  
  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("public") ~> Ok
    case HTTP(GET(UFPath("/https_only"))) => Redirect("https://localhost/https_only")
    case HTTPS(GET(UFPath("/https_only"))) => ResponseString("secret") ~> Ok
    case HTTPS(GET(UFPath("/tryme"))) => ResponseString("secret") ~> Ok
  })}
  
  "A Mixed Secure Server" should {
    "respond to matched unsecure requests" in {
      http(host as_str) must_== "public"
    }
    "redirect to a secure channel" in {
      http(host / "https_only" as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      http(host / "tryme" as_str) must throwA[StatusCode]
    }
  }
}
