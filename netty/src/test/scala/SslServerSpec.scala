package unfiltered.netty

import org.specs._
import unfiltered.spec
import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}

object SslServerSpec extends Specification with spec.netty.Served with spec.SecureClient {
  
  import unfiltered.netty.{Http => NHttp, Ssl}
  import org.apache.http.client.ClientProtocolException
  import dispatch._
  
  // generated keystore for localhost
  // keytool -keystore keystore -alias unfiltered -genkey -keyalg RSA
  val keyStorePath = getClass.getResource("/keystore").getPath
  val keyStorePasswd = "unfiltered"
  val securePort = port
  
  def setup = { port =>
    try {
      val securePlan = new unfiltered.netty.cycle.Plan with Secured {
        import org.jboss.netty.channel.{ChannelHandlerContext, ExceptionEvent}
        
        def intent = { case GET(UFPath("/", _)) => ResponseString("secret") ~> Ok }
        
        override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
          ctx.getChannel.close
        }
      }
      new NHttp(port, "localhost", securePlan :: Nil, () => ()) with Ssl {
        override lazy val keyStore = keyStorePath
        override lazy val keyStorePassword = keyStorePasswd
      }
    } catch { case e => e.printStackTrace 
      throw new RuntimeException(e)
    }
  }
  
  "A Secure Server" should {
    "respond to secure requests" in {
      http(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      http(host as_str) must throwA[ClientProtocolException]
    }
  }
}
