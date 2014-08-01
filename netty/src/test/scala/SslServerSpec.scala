package unfiltered.netty

import unfiltered.spec
import unfiltered.response.{ Ok, ResponseString }
import unfiltered.request.{ GET, Path => UFPath}
import unfiltered.netty.cycle.{ Plan, SynchronousExecution }

import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelHandler.Sharable

import org.apache.http.NoHttpResponseException

import org.specs2.mutable.{BeforeAfter, Specification}

import dispatch.classic._

object SslServerSpec
  extends Specification 
  with unfiltered.specs2.netty.Started
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

  override def xhttp[T](handler: Handler[T]): T =
    super[SecureClient].xhttp(handler)

  step {
    System.setProperty("netty.ssl.keyStore", keyStorePath)
    System.setProperty("netty.ssl.keyStorePassword", keyStorePasswd)
  }

  lazy val server =
    unfiltered.netty.Server.httpsEngine(
        port = securePort,
        host = "localhost",
        ssl  = SslEngineFromPath.Simple(
          keyStorePath,
          keyStorePasswd
        ))
      .plan(new SecurePlan)

  "A Secure Server" should {
    "respond to secure requests" in {
      https(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      https(host as_str) must throwA[NoHttpResponseException]
    }
  }

  step {
    System.clearProperty("netty.ssl.keyStore")
    System.clearProperty("netty.ssl.keyStorePassword")
  }
}
