package unfiltered.server

import unfiltered.specs2.SecureClient
import org.specs2.mutable._

object MixedServerSpec extends Specification with unfiltered.specs2.jetty.Served with SecureClient {
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.Server
  import unfiltered.util.Port

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  override lazy val server = setup(
    Server.http(port).https(
      port = securePort,
      keyStorePath = keyStorePath,
      keyStorePassword = keyStorePasswd
    )
  )

  def setup = { _.plan(unfiltered.filter.Planify {
    case GET(UFPath("/")) => ResponseString("public") ~> Ok
    case HTTP(GET(UFPath("/https_only"))) => Redirect(s"https://localhost:$securePort/https_only")
    case HTTPS(GET(UFPath("/https_only"))) => ResponseString("secret") ~> Ok
    case HTTPS(GET(UFPath("/tryme"))) => ResponseString("secret") ~> Ok
  })}


  "A Mixed Secure Server" should {
    "respond to matched unsecure requests" in {
      https(host).as_string must_== "public"
    }
    "redirect to a secure channel" in {
      https(host / "https_only").as_string must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host / "tryme") must throwA[StatusCode]
    }
  }
}
