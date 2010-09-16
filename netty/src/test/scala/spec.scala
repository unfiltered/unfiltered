package unfiltered.netty

import org.specs.Specification
import org.jboss.netty.buffer.ChannelBuffers
import scala.collection.JavaConversions._
import org.jboss.netty.handler.codec.http._

class RequestSpec extends Specification {

  val payload = "This is the request payload"
  val nettyReq = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/seg1/seg2?param1=value%201&param2=value%202&param2=value%202%20again")
  nettyReq.setContent(ChannelBuffers.copiedBuffer(payload.getBytes("UTF-8")))
  nettyReq.setMethod(HttpMethod.GET)
  nettyReq.setHeader("Single-Header", "A")
  nettyReq.addHeader("Multi-Header", "A")
  nettyReq.addHeader("Multi-Header", "B")

  val req = new RequestBinding(nettyReq)

  "Request Binding" should {
     "return the correct method" in {
       req.getMethod must_== "GET"
     }
    "return an empty enumeration for a missing header" in {
      req.getHeaders("N/A").hasMoreElements() must beFalse
    }
    "return the correct values in a single-header" in {
      val headers = req.getHeaders("Single-Header")
      headers.nextElement() must_== "A"
      headers.hasMoreElements() must beFalse
    }
    "return the correct values in a multi-header" in {
      val headers = req.getHeaders("Multi-Header")
      headers.nextElement() must_== "A"
      headers.nextElement() must_== "B"
    }
    "return url parameters" in {
      req.getParameterValues("param1")(0) must_== "value 1"
    }
    "return a working reader" in {
      // Also tests inputstream
      req.getReader.readLine must_== payload
    }
    "return the request URI without params" in {
      req.getRequestURI must_== "/seg1/seg2"
    }
<<<<<<< HEAD
=======
    "be able to get the Path extracted correctly" in {
      unfiltered.request.Path.unapply(req) must_== Some(Path("/seg1/seg2"))
    }
>>>>>>> dc3ee5942558c7979522a04b82ec828057e843fb
  }
}

class ResponseSpec extends Specification {
  val nettyResp = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
  val resp = new ResponseBinding(nettyResp)
  val payload = "This is the request payload"

  "Response binding" should {
    "return a working outputstream" in {
      val data = payload.getBytes("UTF-8")
      resp.getOutputStream.write(data)
      resp.getOutputStream.close
      resp.underlying.getContent.readableBytes must_== data.length
    }
  }

  "URL Parser" should {
    "return an empty map when no params given" in {
      val url = "/seg1/seg2"
      URLParser.parse(url) must_== Map()
    }
    "return correctly decoded parameters when given" in {
      val url = "/seg1/seg2?param1=value%201&param2=value%202&param2=value%202%20again"
      URLParser.parse(url) must_== Map(
        "param1" -> List("value 1"),
        "param2" -> List("value 2", "value 2 again")
        )
    }
  }

}
