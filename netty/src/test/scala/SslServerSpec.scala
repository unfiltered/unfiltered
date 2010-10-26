package unfiltered.netty

import org.specs._

import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}

object SslServerSpec extends Specification with unfiltered.spec.netty.Served {
  
  import unfiltered.netty.{Http => NHttp, Ssl}
  import org.apache.http.client.ClientProtocolException
  import dispatch._
  
  def setup = { port =>
    try {
      val securePlan = new unfiltered.netty.cycle.Plan with Secured {
        import org.jboss.netty.channel.{ChannelFutureListener,
          ChannelFuture, ChannelHandlerContext, ExceptionEvent}
        
        def intent = { case GET(UFPath("/", _)) => ResponseString("secret") ~> Ok }
        
        override def exceptionCaught(ctx: ChannelHandlerContext, e: ExceptionEvent) = {
          e.getCause.printStackTrace 
          ctx.getChannel.close
        }
      }
      new NHttp(port, "0.0.0.0", securePlan :: Nil, () => ()) with Ssl {
        override lazy val keyStore = getClass.getResource("/keystore").getPath
        override lazy val keyStorePassword = "unfiltered"
      }
    } catch { case e => e.printStackTrace 
      throw new RuntimeException(e)
    }
  }
  
  "A Secure Server" should {
    "respond to secure requests" in {
      Http(host.secure as_str) must_== "secret"
    }
    "refuse connection to unsecure requests" in {
      Http(host as_str) must throwA[ClientProtocolException]
    }
  }
}
