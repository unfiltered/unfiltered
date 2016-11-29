package unfiltered.netty

import java.net.ConnectException

import okhttp3.HttpUrl
import org.specs2.mutable._
import unfiltered.response.{Ok, Redirect, ResponseString}
import unfiltered.request.{GET, HTTP, HTTPS, Path => UFPath}
import unfiltered.specs2.SecureClient
import unfiltered.util.Port

object MixedServerSpec extends Specification with unfiltered.specs2.netty.Served with SecureClient {

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  override lazy val server = setup(
    Server.http(port).httpsEngine(
      port = securePort,
      ssl = SslEngineProvider.path(
      keyStorePath,
      keyStorePasswd
    ))
  )

  val secureHost = HttpUrl.parse(s"https://localhost:$securePort")

  def setup = { _.plan(unfiltered.netty.cycle.Planify {
    case GET(UFPath("/"))                  => ResponseString("public") ~> Ok
    case HTTP(GET(UFPath("/https_only")))  => Redirect(s"https://localhost:$securePort/https_only")
    case HTTPS(GET(UFPath("/https_only"))) => ResponseString("secret") ~> Ok
    case HTTPS(GET(UFPath("/tryme")))      => ResponseString("secret") ~> Ok
  })}

  "A Mixed Secure Server" should {
    "respond to matched unsecure requests" in {
      https(secureHost).as_string must_== "public"
    }
    "redirect to a secure channel" in {
      https(host / "https_only").as_string must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host / "tryme") must throwA[StatusCode]
    }
  }
}
