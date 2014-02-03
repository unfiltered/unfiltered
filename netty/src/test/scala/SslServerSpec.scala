package unfiltered.netty

import unfiltered.spec
import unfiltered.response.{ Ok, ResponseString }
import unfiltered.request.{ GET, Path => UFPath}
import unfiltered.netty.cycle.{ Plan, SynchronousExecution }

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelHandler.Sharable

import org.apache.http.NoHttpResponseException

import org.specs.Specification

import dispatch.classic._

object SslServerSpec
  extends Specification 
  with spec.netty.Started
  with spec.SecureClient {  

  @Sharable
  class SecurePlan extends Plan
    with Secured // also catches netty Ssl errors
    with SynchronousExecution
    with ServerErrorResponse {
    def intent = {
      case GET(UFPath("/")) =>
        ResponseString("secret") ~> Ok
    }
  }
    
  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = port

  doBeforeSpec {
    System.setProperty("netty.ssl.keyStore", keyStorePath)
    System.setProperty("netty.ssl.keyStorePassword", keyStorePasswd)
  }

  doAfterSpec {
    System.clearProperty("netty.ssl.keyStore")
    System.clearProperty("netty.ssl.keyStorePassword")
  }

  lazy val server =
    unfiltered.netty.Https(port, "localhost")
      .handler(new SecurePlan)

  "A Secure Server" should {
    shareVariables()
    "respond to secure requests" in {
      https(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host as_str) must throwA[NoHttpResponseException]
    }
  }
}
