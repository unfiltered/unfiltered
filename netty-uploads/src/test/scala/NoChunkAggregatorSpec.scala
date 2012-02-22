package unfiltered.netty.request

import org.specs._

object NoChunkAggregatorSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath, _}
  import unfiltered.netty
  import unfiltered.netty.{Http => NHttp}
  import dispatch._
  import dispatch.mime.Mime._
  import java.io.{File => JFile}

  def setup = {
    _.handler(netty.async.Planify({
      case r@POST(UFPath("/async/upload") & MultiPart(req)) => 
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => r.respond(ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType)))
        case f =>  r.respond(ResponseString("what's f?"))
      }
    })).handler(netty.cycle.Planify({
      case POST(UFPath("/cycle/upload") & MultiPart(req)) => 
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType))
        case f => ResponseString("what's f?")
      }
    }))
  }

  "When receiving multipart requests with no chunk aggregator, regular netty plans" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }
    
    "respond with a 500 when no chunk aggregator is used in a cycle plan" in {
      val http = new dispatch.Http
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
      val http = new dispatch.Http
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "upload" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 500
        }
      } finally { http.shutdown }
    }
  }
}
