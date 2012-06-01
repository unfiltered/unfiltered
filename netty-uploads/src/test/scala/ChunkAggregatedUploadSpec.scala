package unfiltered.netty.request

import org.specs._

object ChunkAggregatedUploadSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath, _}
  import unfiltered.netty
  import unfiltered.netty.{Http => NHttp}

  import dispatch._
  import dispatch.mime.Mime._
  import java.io.{File => JFile,FileInputStream => FIS}
  import org.apache.commons.io.{IOUtils => IOU}

  def setup = {
    /** Use of a HttpChunkAggregator is mandatory if not using a MultiPartDecoder plan. */
    _.chunked()
    .handler(netty.async.Planify({
      case r@POST(UFPath("/async/disk-upload") & MultiPart(req)) => 
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => r.respond(ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType)))
        case f =>  r.respond(ResponseString("what's f?"))
      }
      case r@POST(UFPath("/async/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile("upload-test-out.txt")) match {
            case Some(outFile) =>
              if(IOU.toString(new FIS(outFile)) == new String(f.bytes)) r.respond(ResponseString(
                "wrote disk read file f named %s with content type %s with correct contents" format(
                  f.name, f.contentType))
              )
              else r.respond(ResponseString(
                "wrote disk read file f named %s with content type %s, with differing contents" format(
                  f.name, f.contentType)))
            case None => r.respond(ResponseString(
              "did not write disk read file f named %s with content type %s" format(
                f.name, f.contentType)))
        }
        case _ =>  r.respond(ResponseString("what's f?"))
      }
      case r@POST(UFPath("/async/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => r.respond(ResponseString(
          "stream read file f is named %s with content type %s" format(
            f.name, f.contentType)))
        case _ =>  r.respond(ResponseString("what's f?"))
      }
      case r@POST(UFPath("/async/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
         case Seq(f, _*) =>
            val src = IOU.toString(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) =>
                if(IOU.toString(new FIS(outFile)) == src) r.respond(ResponseString(
                  "wrote stream read file f named %s with content type %s with correct contents" format(
                    f.name, f.contentType))
                )
                else r.respond(ResponseString(
                  "wrote stream read file f named %s with content type %s, with differing contents" format(
                    f.name, f.contentType)))
              case None => r.respond(ResponseString(
                "did not write stream read file f named %s with content type %s" format(
                  f.name, f.contentType)))
            }
          case _ =>  r.respond(ResponseString("what's f?"))
        }
        case r@POST(UFPath("/async/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) => r.respond(ResponseString(
            "memory read file f is named %s with content type %s" format(
              f.name, f.contentType)))
          case _ =>  r.respond(ResponseString("what's f?"))
        }
        case r@POST(UFPath("/async/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) => r.respond(ResponseString(
                "wrote memory read file f is named %s with content type %s" format(
                  f.name, f.contentType)))
              case None =>  r.respond(ResponseString(
                "did not write memory read file f is named %s with content type %s" format(
                  f.name, f.contentType)))
            }
          case _ =>  r.respond(ResponseString("what's f?"))
        }
    })).handler(netty.cycle.Planify({
      case r@POST(UFPath("/cycle/disk-upload") & MultiPart(req)) => 
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "disk read file f named %s with content type %s" format(
            f.name, f.contentType))
        case f =>  ResponseString("what's f?")
      }
      case r@POST(UFPath("/cycle/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile("upload-test-out.txt")) match {
            case Some(outFile) =>
              if(IOU.toString(new FIS(outFile)) == new String(f.bytes)) ResponseString(
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
        case _ =>  ResponseString("what's f?")
      }
      case r@POST(UFPath("/cycle/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "stream read file f is named %s with content type %s" format(
            f.name, f.contentType))
        case _ => ResponseString("what's f?")
      }
      case r@POST(UFPath("/cycle/stream-upload/write") & MultiPart(req)) =>
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
        case r@POST(UFPath("/cycle/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) => ResponseString(
            "memory read file f is named %s with content type %s" format(
              f.name, f.contentType))
          case _ => ResponseString("what's f?")
        }
        case r@POST(UFPath("/cycle/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
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
      case UFPath("/a") => ResponseString("http response a")
    })
  }

  "MultiPartParams used in netty.cycle.Plan and netty.async.Plan with a chunk aggregator" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }

    /** Async */
    "handle async file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "disk-upload" <<* ("f", file, "text/plain") as_str) must_=="disk read file f named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle async writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "disk-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle async file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "stream-upload" <<* ("f", file, "text/plain") as_str) must_=="stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle async writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle async file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "mem-upload" <<* ("f", file, "text/plain") as_str) must_=="memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "not write memory read files async" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "respond with a 404 async" in {
      val http = new dispatch.Http
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "notfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }

    /** Cycle */
    "handle cycle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "disk-upload" <<* ("f", file, "text/plain") as_str) must_=="disk read file f named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle cycle writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "disk-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle cycle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "stream-upload" <<* ("f", file, "text/plain") as_str) must_=="stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle cycle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
      http(host / "a" as_str) must_==("http response a")
    }
    "handle cycle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "mem-upload" <<* ("f", file, "text/plain") as_str) must_=="memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "not write memory read files cycle" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
      http(host / "a" as_str) must_==("http response a")
    }
    "respond with a 404 cycle" in {
      val http = new dispatch.Http
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
