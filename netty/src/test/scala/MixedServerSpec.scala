package unfiltered.netty

import dispatch.classic._
import org.apache.http.client.ClientProtocolException
import org.specs2.mutable._

import unfiltered.response.{ Ok, Redirect, ResponseString }
import unfiltered.request.{ GET, HTTPS, HTTP, Path => UFPath }
import unfiltered.spec.SecureClient
import unfiltered.util.Port

object MixedServerSpec extends Specification with unfiltered.specs2.netty.Served with SecureClient {

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  override val host = :/("localhost", port)

  override lazy val server = setup(
    Server.http(port).httpsEngine(
      port = securePort,
      ssl = SslEngineFromPath.Simple(
      keyStorePath,
      keyStorePasswd
    ))
  )

  def setup = { _.plan(unfiltered.netty.cycle.Planify {
    case GET(UFPath("/"))                  => ResponseString("public") ~> Ok
    case HTTP(GET(UFPath("/https_only")))  => Redirect("https://localhost/https_only")
    case HTTPS(GET(UFPath("/https_only"))) => ResponseString("secret") ~> Ok
    case HTTPS(GET(UFPath("/tryme")))      => ResponseString("secret") ~> Ok
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
