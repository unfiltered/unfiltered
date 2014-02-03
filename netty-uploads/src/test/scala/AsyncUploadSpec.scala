
package unfiltered.netty.request

import org.specs.Specification

import unfiltered.netty
import unfiltered.netty.async
import unfiltered.netty.{Http => NHttp}
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.spec.netty.Served

import dispatch.classic._
import dispatch.classic.mime.Mime._
import java.io.{File => JFile,FileInputStream => FIS}
import org.apache.commons.io.{IOUtils => IOU}

object AsyncUploadSpec extends Specification
  with Served {

  def setup = {
    val plan = async.MultiPartDecoder({
      case POST(UFPath("/disk-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
          case Seq(f, _*) => binding.respond(ResponseString(
            "disk read file f named %s with content type %s" format(
              f.name, f.contentType)))
          case f =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/disk-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile("upload-test-out.txt")) match {
              case Some(outFile) =>
                if(IOU.toString(new FIS(outFile)) == new String(f.bytes)) binding.respond(ResponseString(
                  "wrote disk read file f named %s with content type %s with correct contents" format(
                    f.name, f.contentType))
                )
                else binding.respond(ResponseString(
                  "wrote disk read file f named %s with content type %s, with differing contents" format(
                    f.name, f.contentType)))
              case None => binding.respond(ResponseString(
                "did not write disk read file f named %s with content type %s" format(
                  f.name, f.contentType)))
          }
          case _ =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/stream-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
          case Seq(f, _*) => binding.respond(ResponseString(
            "stream read file f is named %s with content type %s" format(
              f.name, f.contentType)))
          case _ =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/stream-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
           case Seq(f, _*) =>
              val src = IOU.toString(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
              f.write(new JFile("upload-test-out.txt")) match {
                case Some(outFile) =>
                  if(IOU.toString(new FIS(outFile)) == src) binding.respond(ResponseString(
                    "wrote stream read file f named %s with content type %s with correct contents" format(
                      f.name, f.contentType))
                  )
                  else binding.respond(ResponseString(
                    "wrote stream read file f named %s with content type %s, with differing contents" format(
                      f.name, f.contentType)))
                case None => binding.respond(ResponseString(
                  "did not write stream read file f named %s with content type %s" format(
                    f.name, f.contentType)))
              }
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
        case POST(UFPath("/mem-upload") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
            case Seq(f, _*) => binding.respond(ResponseString(
              "memory read file f is named %s with content type %s" format(
                f.name, f.contentType)))
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
        case POST(UFPath("/mem-upload/write") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
            case Seq(f, _*) =>
              f.write(new JFile("upload-test-out.txt")) match {
                case Some(outFile) => binding.respond(ResponseString(
                  "wrote memory read file f is named %s with content type %s" format(
                    f.name, f.contentType)))
                case None =>  binding.respond(ResponseString(
                  "did not write memory read file f is named %s with content type %s" format(
                    f.name, f.contentType)))
              }
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
    })
    _.handler(plan).handler(planify {
      case _ => NotFound
    })
  }

  "Netty async.MultiPartDecoder" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "disk-upload" <<* ("f", file, "text/plain") as_str) must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "stream-upload" <<* ("f", file, "text/plain") as_str) must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "mem-upload" <<* ("f", file, "text/plain") as_str) must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404" in {
      val http = new dispatch.classic.Http with NoLogging
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "notfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }
  }
}
