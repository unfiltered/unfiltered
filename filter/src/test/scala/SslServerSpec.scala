package unfiltered.server

import unfiltered.specs2.SecureClient
import org.specs2.mutable._
import java.nio.file.Paths

class SslServerSpec extends Specification with unfiltered.specs2.Hosted with SecureClient {

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.Server
  import unfiltered.util.Port

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = {
    val f = getClass.getResource("/keystore").toURI
    if (f.isAbsolute()) {
      Paths.get(f).toAbsolutePath().toString
    } else {
      f.getPath();
    }
  }
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any

  lazy val server = Server.https(
    securePort,
    "0.0.0.0",
    keyStorePath = keyStorePath,
    keyStorePassword = keyStorePasswd
  ).plan(filt)

  lazy val httpServer = Server.http(port, "0.0.0.0").plan(filt)

  step { server.start(); httpServer.start() }

  val filt = unfiltered.filter.Planify(secured.onPass(whatever))
  def secured =
    unfiltered.kit.Secure.redir[Any,Any]( {
      case req @ UFPath(Seg("unprotected" :: Nil)) =>
        Pass
      case req @ UFPath(Seg("protected" :: Nil)) =>
        ResponseString(req.isSecure.toString) ~> Ok
    }, securePort)
  def whatever = unfiltered.Cycle.Intent[Any,Any] {
    case req =>
      ResponseString(req.isSecure.toString) ~> Ok
  }

  "A Secure Server" should {
    "redirect and serve to secure requests" in {
      https(host / "protected").as_string must_== "true"
    }
    "explicit pass to insecure" in {
      https(host / "unprotected").as_string must_== "false"
    }
    "nonmatching pass to insecure" in {
      https(host).as_string must_== "false"
    }
  }

  step {
    server.stop()
    server.destroy()
    httpServer.stop()
    httpServer.destroy()
  }
}
