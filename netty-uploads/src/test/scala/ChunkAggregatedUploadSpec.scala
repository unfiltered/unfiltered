package unfiltered.netty.request

import org.specs2.mutable.Specification

import unfiltered.netty
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.specs2.netty.Served

import java.io.{ File => JFile,FileInputStream => FIS }
import java.util.Arrays

import org.apache.commons.io.{ IOUtils => IOU }

class ChunkAggregatedUploadSpec extends Specification
  with Served {

  private val directory = java.nio.file.Files.createTempDirectory(new JFile("target").toPath, "ChunkAggregatedUploadSpec").toFile

  def setup = {
    _.plan(netty.async.Planify({
      case POST(UFPath("/async/disk-upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => req.respond(ResponseString(
          s"disk read file f named ${f.name} with content type ${f.contentType}"))
        case f => req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile(directory, "1async-upload-test-out.txt")) match {
            case Some(outFile) =>
              if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), f.bytes)) req.respond(ResponseString(
                s"wrote disk read file f named ${f.name} with content type ${f.contentType} with correct contents")
              )
              else req.respond(ResponseString(
                s"wrote disk read file f named ${f.name} with content type ${f.contentType}, with differing contents"))
            case None => req.respond(ResponseString(
              s"did not write disk read file f named ${f.name} with content type ${f.contentType}"))
        }
        case _ =>  req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => req.respond(ResponseString(
          s"stream read file f is named ${f.name} with content type ${f.contentType}"))
        case _ =>  req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
          case Seq(f, _*) =>
            val src = IOU.toByteArray(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
            f.write(new JFile(directory, "2async-upload-test-out.txt")) match {
              case Some(outFile) =>
                if (Arrays.equals(IOU.toByteArray(new FIS(outFile)), src)) req.respond(ResponseString(
                  s"wrote stream read file f named ${f.name} with content type ${f.contentType} with correct contents")
                )
                else req.respond(ResponseString(
                  s"wrote stream read file f named ${f.name} with content type ${f.contentType}, with differing contents"))
              case None => req.respond(ResponseString(
                s"did not write stream read file f named ${f.name} with content type ${f.contentType}"))
            }
          case _ =>  req.respond(ResponseString("what's f?"))
        }
      case POST(UFPath("/async/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
        case Seq(f, _*) => req.respond(ResponseString(
          s"memory read file f is named ${f.name} with content type ${f.contentType}"))
        case _ =>  req.respond(ResponseString("what's f?"))
      }
      case POST(UFPath("/async/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile(directory, "3async-upload-test-out.txt")) match {
            case Some(outFile) => req.respond(ResponseString(
              s"wrote memory read file f is named ${f.name} with content type ${f.contentType}"))
            case None =>  req.respond(ResponseString(
              s"did not write memory read file f is named ${f.name} with content type ${f.contentType}"))
          }
        case _ => req.respond(ResponseString("what's f?"))
      }
    })).plan(netty.cycle.Planify({
      case POST(UFPath("/cycle/disk-upload") & MultiPart(req)) =>
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          s"disk read file f named ${f.name} with content type ${f.contentType}")
        case f => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile(directory, "1upload-test-out.txt")) match {
            case Some(outFile) =>
              if (Arrays.equals(IOU.toByteArray(new FIS(outFile)), f.bytes)) ResponseString(
                s"wrote disk read file f named ${f.name} with content type ${f.contentType} with correct contents"
              )
              else ResponseString(
                s"wrote disk read file f named ${f.name} with content type ${f.contentType}, with differing contents")
            case None => ResponseString(
              s"did not write disk read file f named ${f.name} with content type ${f.contentType}")
        }
        case _ => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          s"stream read file f is named ${f.name} with content type ${f.contentType}")
        case _ => ResponseString("what's f?")
      }
      case POST(UFPath("/cycle/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
          case Seq(f, _*) =>
            val src = IOU.toByteArray(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
            f.write(new JFile(directory, "2upload-test-out.txt")) match {
              case Some(outFile) =>
                if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), src)) ResponseString(
                  s"wrote stream read file f named ${f.name} with content type ${f.contentType} with correct contents")
                else ResponseString(
                  s"wrote stream read file f named ${f.name} with content type ${f.contentType}, with differing contents")
              case None => ResponseString(
                s"did not write stream read file f named ${f.name} with content type ${f.contentType}")
            }
          case _ => ResponseString("what's f?")
        }
        case POST(UFPath("/cycle/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) => ResponseString(
            s"memory read file f is named ${f.name} with content type ${f.contentType}")
          case _ => ResponseString("what's f?")
        }
        case POST(UFPath("/cycle/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
          case Seq(f, _*) =>
            f.write(new JFile(directory, "3upload-test-out.txt")) match {
              case Some(outFile) => ResponseString(
                s"wrote memory read file f is named ${f.name} with content type ${f.contentType}")
              case None => ResponseString(
                s"did not write memory read file f is named ${f.name} with content type ${f.contentType}")
            }
          case _ => ResponseString("what's f?")
        }
    })).plan(planify {
      case _ => NotFound
    })
  }

 "MultiPartParams used in netty.cycle.Plan and netty.async.Plan with a chunk aggregator" should {
    // Async
    "handle async file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "disk-upload").<<*("f", file, "text/plain")).as_string must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle async writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "disk-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle async file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "stream-upload").<<*("f", file, "text/plain")).as_string must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle async writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "stream-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle async file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "mem-upload").<<*("f", file, "text/plain")).as_string must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files async" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "mem-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404 async" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true

      val resp = httpx(req(host / "async" / "notfound").<<*("f", file, "text/plain"))
      resp.code must_== 404
    }

    // Cycle
    "handle cycle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "disk-upload").<<*("f", file, "text/plain")).as_string must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle cycle writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "disk-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote disk read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle cycle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "stream-upload").<<*("f", file, "text/plain")).as_string must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle cycle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "stream-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle cycle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "mem-upload").<<*("f", file, "text/plain")).as_string must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files cycle" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "mem-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404 cycle" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true

      val resp = httpx(req(host / "cycle" / "notfound").<<*("f", file, "text/plain"))
      resp.code must_== 404
    }
  }
}
