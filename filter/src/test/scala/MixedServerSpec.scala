package unfiltered.server

import unfiltered.spec.SecureClient
import org.specs2.mutable._

object MixedServerSpec extends Specification with unfiltered.specs2.jetty.Served with SecureClient {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.Server
  import unfiltered.util.Port
  import org.apache.http.client.ClientProtocolException

  import dispatch.classic._

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  override val host = :/("localhost", port)

  override lazy val server = setup(
    Server.http(port).https(
      port = securePort,
      keyStorePath = keyStorePath,
      keyStorePassword = keyStorePasswd
    )
  )

  def setup = { _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("public") ~> Ok
    case HTTP(GET(UFPath("/https_only"))) => Redirect("https://localhost/https_only")
    case HTTPS(GET(UFPath("/https_only"))) => ResponseString("secret") ~> Ok
    case HTTPS(GET(UFPath("/tryme"))) => ResponseString("secret") ~> Ok
  })}

  override def xhttp[T](handler: Handler[T]): T =
    super[SecureClient].xhttp(handler)

  "A Mixed Secure Server" should {
    "respond to matched unsecure requests" in {
      https(host as_str) must_== "public"
    }
    "redirect to a secure channel" in {
      https(host / "https_only" as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host / "tryme" as_str) must throwA[StatusCode]
    }
  }
}
