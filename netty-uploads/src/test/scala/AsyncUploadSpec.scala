
package unfiltered.netty.request

import org.specs2.mutable.Specification

import unfiltered.netty.async
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.specs2.netty.Served

import java.io.{File => JFile,FileInputStream => FIS}
import java.util.Arrays
import org.apache.commons.io.{IOUtils => IOU}

class AsyncUploadSpec extends Specification
  with Served {

  private val directory = java.nio.file.Files.createTempDirectory(new JFile("target").toPath, "AsyncUploadSpec").toFile

  def setup = {
    val plan = async.MultiPartDecoder({
      case POST(UFPath("/disk-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
          case Seq(f, _*) => binding.respond(ResponseString(
            s"disk read file f named ${f.name} with content type ${f.contentType}"))
          case f =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/disk-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile(directory, "1upload-test-out.txt")) match {
              case Some(outFile) =>
                if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), f.bytes)) binding.respond(ResponseString(
                  s"wrote disk read file f named ${f.name} with content type ${f.contentType} with correct contents")
                )
                else binding.respond(ResponseString(
                  s"wrote disk read file f named ${f.name} with content type ${f.contentType}, with differing contents"))
              case None => binding.respond(ResponseString(
                s"did not write disk read file f named ${f.name} with content type ${f.contentType}"))
          }
          case _ =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/stream-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
          case Seq(f, _*) => binding.respond(ResponseString(
            s"stream read file f is named ${f.name} with content type ${f.contentType}"))
          case _ =>  binding.respond(ResponseString("what's f?"))
        }
      }
      case POST(UFPath("/stream-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
           case Seq(f, _*) =>
              val src = IOU.toByteArray(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
              f.write(new JFile(directory, "2upload-test-out.txt")) match {
                case Some(outFile) =>
                  if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), src)) binding.respond(ResponseString(
                    s"wrote stream read file f named ${f.name} with content type ${f.contentType} with correct contents")
                  )
                  else binding.respond(ResponseString(
                    s"wrote stream read file f named ${f.name} with content type ${f.contentType}, with differing contents"))
                case None => binding.respond(ResponseString(
                  s"did not write stream read file f named ${f.name} with content type ${f.contentType}"))
              }
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
        case POST(UFPath("/mem-upload") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
            case Seq(f, _*) => binding.respond(ResponseString(
              s"memory read file f is named ${f.name} with content type ${f.contentType}"))
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
        case POST(UFPath("/mem-upload/write") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
            case Seq(f, _*) =>
              f.write(new JFile(directory, "3upload-test-out.txt")) match {
                case Some(outFile) => binding.respond(ResponseString(
                  s"wrote memory read file f is named ${f.name} with content type ${f.contentType}"))
                case None =>  binding.respond(ResponseString(
                  s"did not write memory read file f is named ${f.name} with content type ${f.contentType}"))
              }
            case _ => binding.respond(ResponseString("what's f?"))
          }
        }
    })
    _.plan(plan).plan(planify {
      case _ => NotFound
    })
  }

  "Netty async.MultiPartDecoder" should {
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "disk-upload").<<*("f", file, "text/plain")).as_string must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload").<<*("f", file, "text/plain")).as_string must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload").<<*("f", file, "text/plain")).as_string must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val resp = httpx(req(host / "notfound").<<*("f", file, "text/plain"))
      resp.code must_== 404
    }
  }
}
