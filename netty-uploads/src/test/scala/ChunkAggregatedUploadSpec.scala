package unfiltered.netty.request

import org.specs.Specification

import unfiltered.netty
import unfiltered.netty.{ Http => NHttp }
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.spec.netty.Served
import dispatch.classic._
import dispatch.classic.mime.Mime._

import java.io.{ File => JFile,FileInputStream => FIS }

import org.apache.commons.io.{ IOUtils => IOU }

object ChunkAggregatedUploadSpec extends Specification
  with Served {

  def setup = {
    /** Use of a HttpChunkAggregator is mandatory if not using a MultiPartDecoder plan.
     *  note(doug): for netty4 releases. this is not true */
    _.chunked()
    .handler(netty.async.Planify({
      case POST(UFPath("/async/disk-upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => req.respond(ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType)))
        case f => req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile("upload-test-out.txt")) match {
            case Some(outFile) =>
              if(IOU.toString(new FIS(outFile)) == new String(f.bytes)) req.respond(ResponseString(
                "wrote disk read file f named %s with content type %s with correct contents" format(
                  f.name, f.contentType))
              )
              else req.respond(ResponseString(
                "wrote disk read file f named %s with content type %s, with differing contents" format(
                  f.name, f.contentType)))
            case None => req.respond(ResponseString(
              "did not write disk read file f named %s with content type %s" format(
                f.name, f.contentType)))
        }
        case _ =>  req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => req.respond(ResponseString(
          "stream read file f is named %s with content type %s" format(
            f.name, f.contentType)))
        case _ =>  req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
         case Seq(f, _*) =>
            val src = IOU.toString(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) =>
                if (IOU.toString(new FIS(outFile)) == src) req.respond(ResponseString(
                  "wrote stream read file f named %s with content type %s with correct contents" format(
                    f.name, f.contentType))
                )
                else req.respond(ResponseString(
                  "wrote stream read file f named %s with content type %s, with differing contents" format(
                    f.name, f.contentType)))
              case None => req.respond(ResponseString(
                "did not write stream read file f named %s with content type %s" format(
                  f.name, f.contentType)))
            }
          case _ =>  req.respond(ResponseString("what's f?"))
        }
        case POST(UFPath("/async/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) => req.respond(ResponseString(
            "memory read file f is named %s with content type %s" format(
              f.name, f.contentType)))
          case _ =>  req.respond(ResponseString("what's f?"))
        }
        case POST(UFPath("/async/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) => req.respond(ResponseString(
                "wrote memory read file f is named %s with content type %s" format(
                  f.name, f.contentType)))
              case None =>  req.respond(ResponseString(
                "did not write memory read file f is named %s with content type %s" format(
                  f.name, f.contentType)))
            }
          case _ => req.respond(ResponseString("what's f?"))
        }
    })).handler(netty.cycle.Planify({
      case POST(UFPath("/cycle/disk-upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType))
        case f => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile("upload-test-out.txt")) match {
            case Some(outFile) =>
              if (IOU.toString(new FIS(outFile)) == new String(f.bytes)) ResponseString(
                "wrote disk read file f named %s with content type %s with correct contents" format(
                  f.name, f.contentType)
              )
              else ResponseString(
                "wrote disk read file f named %s with content type %s, with differing contents" format(
                  f.name, f.contentType))
            case None => ResponseString(
              "did not write disk read file f named %s with content type %s" format(
                f.name, f.contentType))
        }
        case _ => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "stream read file f is named %s with content type %s" format(
            f.name, f.contentType))
        case _ => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
         case Seq(f, _*) =>
            val src = IOU.toString(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) =>
                if(IOU.toString(new FIS(outFile)) == src) ResponseString(
                  "wrote stream read file f named %s with content type %s with correct contents" format(
                    f.name, f.contentType))
                else ResponseString(
                  "wrote stream read file f named %s with content type %s, with differing contents" format(
                    f.name, f.contentType))
              case None => ResponseString(
                "did not write stream read file f named %s with content type %s" format(
                  f.name, f.contentType))
            }
          case _ => ResponseString("what's f?")
        }
        case POST(UFPath("/cycle/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) => ResponseString(
            "memory read file f is named %s with content type %s" format(
              f.name, f.contentType))
          case _ => ResponseString("what's f?")
        }
        case POST(UFPath("/cycle/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) => ResponseString(
                "wrote memory read file f is named %s with content type %s" format(
                  f.name, f.contentType))
              case None => ResponseString(
                "did not write memory read file f is named %s with content type %s" format(
                  f.name, f.contentType))
            }
          case _ => ResponseString("what's f?")
        }
    })).handler(planify {
      case _ => NotFound
    })
  }

 "MultiPartParams used in netty.cycle.Plan and netty.async.Plan with a chunk aggregator" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if (out.exists) out.delete
    }

    // Async
    "handle async file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "disk-upload" <<* ("f", file, "text/plain") as_str) must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle async writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "disk-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle async file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "stream-upload" <<* ("f", file, "text/plain") as_str) must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle async writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle async file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "mem-upload" <<* ("f", file, "text/plain") as_str) must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files async" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404 async" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "notfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }

    // Cycle
    "handle cycle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "disk-upload" <<* ("f", file, "text/plain") as_str) must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle cycle writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "disk-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle cycle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "stream-upload" <<* ("f", file, "text/plain") as_str) must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle cycle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle cycle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "mem-upload" <<* ("f", file, "text/plain") as_str) must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files cycle" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404 cycle" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "cycle" / "notfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }
  }
}
