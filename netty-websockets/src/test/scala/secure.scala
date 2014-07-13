package unfiltered.netty.websockets

import java.util.concurrent.{ CountDownLatch, TimeUnit }

import org.jboss.netty.channel.{ExceptionEvent, ChannelHandlerContext}
import org.apache.http.NoHttpResponseException

import unfiltered.netty.{ServerErrorResponse, Secured}
import unfiltered.netty.cycle.{SynchronousExecution, Plan => NPlan}
import unfiltered.request.{Path => UFPath, GET}
import unfiltered.response.{ResponseString, Ok}
import unfiltered.netty.Https

import tubesocks.{
  Open => TOpen,
  Close => TClose,
  Message => TMessage,
  Error => TError,
  _
}

object WebSocketSecurePlanSpec
  extends org.specs.Specification
  with unfiltered.spec.netty.Started
  with unfiltered.spec.SecureClient {

  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = port

  def wsuri = host.to_uri.toString.replace("http", "ws")
  def wssuri = host.secure.to_uri.toString.replace("https", "wss")

  doBeforeSpec {
    System.setProperty("netty.ssl.keyStore", keyStorePath)
    System.setProperty("netty.ssl.keyStorePassword", keyStorePasswd)
  }
  doAfterSpec {
    System.clearProperty("netty.ssl.keyStore")
    System.clearProperty("netty.ssl.keyStorePassword")
  }

  lazy val server = {
    try {
      val securePlan = new Plan with SynchronousExecution
        with ServerErrorResponse {

        def pass = DefaultPassHandler

        def intent = {
          case GET(UFPath("/")) => {
            case Open(s) =>
              s.send("open")
            case Message(s, Text(m)) =>
              s.send(m)
          }
        }

        override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) =
          ctx.getChannel.close
      }

      Https(port, "localhost").handler(securePlan)
    } catch { case e =>
      e.printStackTrace
      throw new RuntimeException(e)
    }
  }

  "A Secure Websocket Server" should {
    "respond to secure requests" in {
      var s = ""
      val l = new CountDownLatch(2)
      Sock.uri(wssuri) {
        case TOpen(s) =>
          s.send("open")
        case TMessage(t, _) =>
          s += t
          l.countDown
      }
      l.await(10, TimeUnit.MILLISECONDS)
      s must_== "openopen"
    }

    "refuse unsecure websocket requests" in {
      var ex = ""
      val l = new CountDownLatch(1)
      try {
        Sock.uri(wsuri) {
          case TError(e) =>
            ex = e.getClass.getSimpleName
            l.countDown
        }
      } catch {
        case _: Exception => ()
      }
      l.await(10, TimeUnit.MILLISECONDS)
      ex must_== "IOException"
    }

    "refuse unsecure HTTP requests" in {
      https(host as_str) must throwA[NoHttpResponseException]
    }
  }
}
