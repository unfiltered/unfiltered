package unfiltered.netty.request

import org.specs._

object NoChunkAggregatorSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath, _}
  import unfiltered.netty
  import unfiltered.netty.{Http => NHttp, ExceptionHandler}
  import unfiltered.netty.cycle.ThreadPool
  import dispatch.classic._
  import dispatch.classic.mime.Mime._
  import java.io.{File => JFile}

  trait ExpectedServerErrorResponse { self: ExceptionHandler =>
    import org.jboss.netty.channel.{ChannelFutureListener, ChannelHandlerContext, ExceptionEvent}
    import org.jboss.netty.handler.codec.http._
    import org.jboss.netty.buffer.ChannelBuffers
    def onException(ctx: ChannelHandlerContext, t: Throwable) {
      println("here!")
      val ch = ctx.getChannel
      if (ch.isOpen) try {
        println("expected exception occured: '%s'" format t.getMessage())
        val res = new DefaultHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR)
        res.setContent(ChannelBuffers.copiedBuffer(
          "Internal Server Error".getBytes("utf-8")))
        ch.write(res).addListener(ChannelFutureListener.CLOSE)
      } catch {
        case _ => ch.close()
      }
    }
  }

  def setup = {
    _.handler(new netty.async.Plan with ExpectedServerErrorResponse {
      def intent = {
        case POST(UFPath("/async/upload")) => Pass
      }
    })
    .handler(netty.cycle.Planify({
      case POST(UFPath("/cycle/upload")) => Pass
    }))
    .handler(netty.cycle.MultiPartDecoder({
      case POST(UFPath("/cycle/upload") & MultiPart(req)) => netty.cycle.MultipartPlan.Pass
    }))
    .handler(netty.async.MultiPartDecoder({
      case POST(UFPath("/async/upload") & MultiPart(req)) => netty.async.MultipartPlan.Pass
    }))
    .handler(netty.async.Planify {
      case r@POST(UFPath("/async/upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
          case Seq(f, _*) => r.respond(ResponseString(
            "disk read file f named %s with content type %s" format(
              f.name, f.contentType)))
          case f =>  r.respond(ResponseString("what's f?"))
        }
    })
    .handler(new netty.cycle.Plan with ThreadPool with ExpectedServerErrorResponse {
      def intent = {
        case POST(UFPath("/cycle/upload") & MultiPart(req)) =>
          MultiPartParams.Disk(req).files("f") match {
            case Seq(f, _*) => ResponseString(
              "disk read file f named %s with content type %s" format(
                f.name, f.contentType))
            case f => ResponseString("what's f?")
          }
      }
    })
  }

  "When receiving multipart requests with no chunk aggregator, regular netty plans" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }

    "respond with a 500 when no chunk aggregator is used in a cycle plan" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "cycle" / "upload" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 500
        }
      } finally { http.shutdown }
    }

    "respond with a 500 when no chunk aggregator is used in an async plan" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "upload" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 500
        }
      } finally { http.shutdown }
    }

    "handle multipart uploads which are not chunked" in {
      /** This assumes Dispatch doesn't build a chunked request because the data is small */
      val file = new JFile(getClass.getResource("/netty-upload-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "upload" <<* ("f", file, "text/plain") as_str) must_== "disk read file f named netty-upload-test.txt with content type text/plain"
      http(host / "cycle" / "upload" <<* ("f", file, "text/plain") as_str) must_== "disk read file f named netty-upload-test.txt with content type text/plain"
    }
  }
}
