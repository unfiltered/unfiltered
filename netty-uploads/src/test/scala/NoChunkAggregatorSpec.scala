package unfiltered.netty.request

import unfiltered.netty
import unfiltered.netty.{ Http => NHttp, ExceptionHandler }
import unfiltered.netty.cycle.ThreadPool
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ Pass, ResponseString }

import dispatch.classic._
import dispatch.classic.mime.Mime._

import java.io.{ File => JFile }

import io.netty.buffer.Unpooled
import io.netty.channel.{ ChannelFutureListener, ChannelHandlerContext }
import io.netty.channel.ChannelHandler.Sharable
import io.netty.handler.codec.http.{ DefaultFullHttpResponse, HttpResponseStatus, HttpVersion }

import org.specs.Specification

object NoChunkAggregatorSpec extends Specification
  with unfiltered.spec.netty.Served {

  trait ExpectedServerErrorResponse { self: ExceptionHandler =>
    def onException(ctx: ChannelHandlerContext, t: Throwable) {
      val ch = ctx.channel
      if (ch.isOpen) try {
        println("expected exception occured: '%s'" format t.getMessage())
        val res = new DefaultFullHttpResponse(
          HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR,
          Unpooled.copiedBuffer(
            HttpResponseStatus.INTERNAL_SERVER_ERROR.toString.getBytes("utf-8")))
        ch.write(res).addListener(ChannelFutureListener.CLOSE)
      } catch {
        case _ => ch.close()
      }
    }
  }

  // note(doug): this would previous trigger a 500 error but no longer does do to the fact that the default pipeline includes chunk aggregation
  @Sharable
  class ExpectedErrorAsyncPlan extends netty.async.Plan with ExpectedServerErrorResponse {
    def intent = {
      case POST(UFPath("/async/upload")) =>
        Pass
    }
  }

  // note(doug): this would previous trigger a 500 error but no longer does do to the fact that the default pipeline includes chunk aggregation
  @Sharable
  class ExpectedErrorCyclePlan extends netty.cycle.Plan with ThreadPool with ExpectedServerErrorResponse {
    def intent = {
      case POST(UFPath("/cycle/upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
          case Seq(f, _*) => ResponseString(
            "disk read file f named %s with content type %s" format(
              f.name, f.contentType))
          case f => ResponseString("what's f?")
        }
    }
  }

  def setup = {
    _.handler(new ExpectedErrorAsyncPlan)
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
    .handler(new ExpectedErrorCyclePlan)
  }

  "When receiving multipart requests with no chunk aggregator, regular netty plans" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if (out.exists) out.delete
    }

    // note(doug): in netty3 versions of unfiltered this would result in a 500 error
    "respond with a 200 when no chunk aggregator is used in a cycle plan" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "cycle" / "upload" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 200
        }
      } finally { http.shutdown }
    }

    // note(doug): in netty3 versions of unfiltered this would result in a 500 error
    "respond with a 200 when no chunk aggregator is used in an async plan" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "upload" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 200
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
