package unfiltered.netty

import unfiltered.spec
import unfiltered.response.{ Ok, ResponseString }
import unfiltered.request.{ GET, Path => UFPath}
import unfiltered.netty.cycle.{ Plan, SynchronousExecution }
import io.netty.handler.ssl.{
  NotSslRecordException,
  SslHandshakeCompletionEvent
}
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelHandler.Sharable

import org.apache.http.NoHttpResponseException

import org.specs2.mutable.{ BeforeAfter, Specification }

import dispatch.classic._

object SslServerSpec
  extends Specification 
  with unfiltered.specs2.netty.Started
  with spec.SecureClient {  

  @Sharable
  class SecurePlan extends Plan
    with SynchronousExecution
    with ServerErrorResponse {
    def intent = {
      case GET(UFPath("/")) =>
        ResponseString("secret") ~> Ok
    }

    // ignoring not ssl record exception
    override def onException(
       ctx: ChannelHandlerContext, t: Throwable): Unit = t match {
      case sslerr: NotSslRecordException  => ()
      case other => super.onException(ctx, t)
    }

    // how to get notified of handshake events
    override def userEventTriggered(
      ctx: ChannelHandlerContext,
      event: java.lang.Object): Unit = event match {
      case s: SslHandshakeCompletionEvent =>
        // do something with result of handshake
      case e =>
        ctx.fireUserEventTriggered(e)
    }
  }
    
  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = port

  override def xhttp[T](handler: Handler[T]): T =
    super[SecureClient].xhttp(handler)

  lazy val server =
    Server.httpsEngine(
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
}
