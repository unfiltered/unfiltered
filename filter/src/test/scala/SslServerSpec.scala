package unfiltered.server

import unfiltered.spec
import org.specs._

object SslServerSpec extends Specification with unfiltered.spec.Hosted with spec.SecureClient {
  shareVariables()

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.jetty.{Https,Http=>UFHttp}
  import unfiltered.util.Port
  import org.apache.http.client.ClientProtocolException

  import dispatch._

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = Port.any
  val httpPort = Port.any

  override val host = :/("localhost", httpPort)

  lazy val server = new Https(securePort, "0.0.0.0") {
    filter(filt)
    override lazy val keyStore = keyStorePath
    override lazy val keyStorePassword = keyStorePasswd
  }

  lazy val httpServer = new UFHttp(httpPort, "0.0.0.0").filter(filt)

  doBeforeSpec { server.start(); httpServer.start() }
  doAfterSpec {
    server.stop()
    server.destroy()
    httpServer.stop()
    httpServer.destroy()
  }

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
      https(host / "protected" as_str) must_== "true"
    }
    "explicit pass to insecure" in {
      https(host / "unprotected" as_str) must_== "false"
    }
    "nonmatching pass to insecure" in {
      https(host as_str) must_== "false"
    }
  }
}
