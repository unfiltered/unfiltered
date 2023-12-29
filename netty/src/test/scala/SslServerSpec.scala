package unfiltered.netty

import unfiltered.specs2.SecureClient
import unfiltered.response.Ok
import unfiltered.response.ResponseString
import unfiltered.request.GET
import unfiltered.request.{Path => UFPath}
import unfiltered.netty.cycle.Plan
import unfiltered.netty.cycle.SynchronousExecution
import io.netty.handler.ssl.NotSslRecordException
import io.netty.handler.ssl.SslHandshakeCompletionEvent
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelHandler.Sharable
import org.specs2.mutable.Specification
import java.nio.file.Paths

class SslServerSpec extends Specification with unfiltered.specs2.netty.Started with SecureClient {

  @Sharable
  class SecurePlan extends Plan with SynchronousExecution with ServerErrorResponse {
    def intent = { case GET(UFPath("/")) =>
      ResponseString("secret") ~> Ok
    }

    // ignoring not ssl record exception
    override def onException(ctx: ChannelHandlerContext, t: Throwable): Unit = t match {
      case sslerr: NotSslRecordException => ()
      case other => super.onException(ctx, t)
    }

    // how to get notified of handshake events
    override def userEventTriggered(ctx: ChannelHandlerContext, event: java.lang.Object): Unit = event match {
      case s: SslHandshakeCompletionEvent =>
      // do something with result of handshake
      case e =>
        ctx.fireUserEventTriggered(e)
    }
  }

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = {
    val f = getClass.getResource("/keystore").toURI
    if (f.isAbsolute()) {
      Paths.get(f).toAbsolutePath().toString
    } else {
      f.getPath()
    }
  }
  val keyStorePasswd = "unfiltered"
  val securePort = port

  lazy val server =
    Server
      .httpsEngine(
        port = securePort,
        host = "localhost",
        ssl = SslEngineProvider.path(
          keyStorePath,
          keyStorePasswd
        )
      )
      .plan(new SecurePlan)

  "A Secure Server" should {
    "respond to secure requests" in {
      https(host.newBuilder().scheme("https").build()).as_string must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      try {
        https(host)
        sys.error("unexpected")
      } catch {
        case (_: java.net.ConnectException) | (_: java.io.IOException) =>
          ok
      }
    }
  }
}
