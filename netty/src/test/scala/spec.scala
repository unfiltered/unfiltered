package unfiltered.netty

import org.specs2.mutable.Specification
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.DefaultFullHttpRequest
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.HttpVersion

// todo: move to its own file
class RequestSpec extends Specification {

  val payload = "This is the request payload"
  val nettyReq = new DefaultFullHttpRequest(
    HttpVersion.HTTP_1_1,
    HttpMethod.GET,
    "/seg1/seg2?param1=value%201&param2=value%202&param2=value%202%20again&param3=value=mapping",
    Unpooled.copiedBuffer(payload.getBytes("UTF-8"))
  )
  nettyReq.headers.add("Single-Header", "A")
  nettyReq.headers.add("Multi-Header", "A")
  nettyReq.headers.add("Multi-Header", "B")

  val req = new RequestBinding(ReceivedMessage(nettyReq, null, null))

  "Request Binding" should {
    "return the correct method" in {
      req.method must_== "GET"
    }
    "return an empty enumeration for a missing header" in {
      req.headers("N/A").hasNext must beFalse
    }
    "return the correct values in a single-header" in {
      val headers = req.headers("Single-Header")
      headers.next() must_== "A"
      headers.hasNext must beFalse
    }
    "return the correct values in a multi-header" in {
      val headers = req.headers("Multi-Header")
      headers.next() must_== "A"
      headers.next() must_== "B"
    }
    "return url parameters" in {
      req.parameterValues("param1")(0) must_== "value 1"
    }
    "return url parameters with non-encoded =" in {
      req.parameterValues("param3")(0) must_== "value=mapping"
    }
    "return empty seq for missing parameter with other parameters present" in {
      req.parameterValues("param42") must_== Seq.empty
    }
    "return empty seq for missing parameter when no parameters are present at all" in {
      val nreq = new DefaultFullHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, "/")
      val req = new RequestBinding(ReceivedMessage(nreq, null, null))
      req.parameterValues("param42") must_== Seq.empty
    }
    "return a working reader" in {
      // Also tests inputstream
      req.reader.readLine must_== payload
    }
    "return the request URI without params" in {
      req.uri.split('?')(0) must_== "/seg1/seg2"
    }
    "be able to get the Path extracted correctly" in {
      val unfiltered.request.Path(path) = req
      path must_== "/seg1/seg2"
    }
    "extract QueryString correctly" in {
      val unfiltered.request.QueryString(qs) = req
      qs must_== "param1=value%201&param2=value%202&param2=value%202%20again&param3=value=mapping"
    }
  }
}

// todo: move to its own file
class ResponseSpec extends Specification {
  val nettyResp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
  val resp = new ResponseBinding(nettyResp)
  val payload = "This is the request payload"

  "Response binding" should {
    "return a working outputstream" in {
      val data = payload.getBytes("UTF-8")
      resp.outputStream.write(data)
      resp.outputStream.close
      resp.underlying.content.readableBytes must_== data.length
    }
  }

  "URL Parser" should {
    "return an empty map when no params given" in {
      val url = ""
      URLParser.urldecode(url) must beEmpty
    }
    "return correctly decoded parameters when given" in {
      val url = "param1=value%201&param2=value%202&param2=value%202%20again&param%3A3=value%203&param4=value=mapping"
      val m = URLParser.urldecode(url)
      m("param1") must_== List("value 1")
      m("param2") must_== List("value 2", "value 2 again")
      m("param:3") must_== List("value 3")
      m("param4") must_== List("value=mapping")
    }
  }
}
