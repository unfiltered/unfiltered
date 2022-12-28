package unfiltered.netty.request

import org.specs2.mutable.Specification

import unfiltered.netty.cycle
import unfiltered.request.{ Path => UFPath, POST, PUT, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.specs2.netty.Served

import java.io.{ File => JFile,FileInputStream => FIS }
import java.util.Arrays

import org.apache.commons.io.{ IOUtils => IOU }

import okhttp3.{MediaType, MultipartBody, Request, RequestBody}

class CycleUploadSpec extends Specification
  with Served {
  private val directory = java.nio.file.Files.createTempDirectory(new JFile("target").toPath, "CycleUploadSpec").toFile

  def setup = {
    val plan = cycle.MultiPartDecoder({
      case POST(UFPath("/disk-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
          case Seq(f, _*) => ResponseString(
            s"disk read file f named ${f.name} with content type ${f.contentType}")
          case f => ResponseString("what's f?")
        }
      }
      case POST(UFPath("/disk-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
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
        }
      case POST(UFPath("/stream-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
            case Seq(f, _*) => ResponseString(
              s"stream read file f is named ${f.name} with content type ${f.contentType}")
            case _ => ResponseString("what's f?")
          }
      }
      case POST(UFPath("/stream-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
           case Seq(f, _*) =>
              val src = IOU.toByteArray(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
              f.write(new JFile(directory, "2upload-test-out.txt")) match {
                case Some(outFile) =>
                  if (Arrays.equals(IOU.toByteArray(new FIS(outFile)), src)) ResponseString(
                    s"wrote stream read file f named ${f.name} with content type ${f.contentType} with correct contents"
                  )
                  else ResponseString(
                    s"wrote stream read file f named ${f.name} with content type ${f.contentType}, with differing contents")
                case None => ResponseString(
                  s"did not write stream read file f named ${f.name} with content type ${f.contentType}")
              }
            case _ => ResponseString("what's f?")
          }
        }
        case POST(UFPath("/mem-upload") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
              case Seq(f, _*) => ResponseString(
                s"memory read file f is named ${f.name} with content type ${f.contentType}")
              case _ => ResponseString("what's f?")
            }
        }
        case POST(UFPath("/mem-upload/write") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
              case Seq(f, _*) =>
                f.write(new JFile(directory, "3upload-test-out.txt")) match {
                  case Some(outFile) => ResponseString(
                    s"wrote memory read file f is named ${f.name} with content type ${f.contentType}")
                  case None => ResponseString(
                    s"did not write memory read file f is named ${f.name} with content type ${f.contentType}")
                }
              case _ => ResponseString("what's f?")
            }
        }
        case
          PUT(UFPath("/disk-upload") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Disk(binding).files("f") match {
              case Seq(f, _*) => ResponseString(
                s"disk read file f named ${f.name} with content type ${f.contentType}")
              case f => ResponseString("what's f?")
            }
        }
    })
    _.plan(plan).plan(cycle.Planify {
      case _ => NotFound
    })
  }

  "Netty cycle.MultiPartDecoder" should {
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
    "handle file uploads via PUT" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val mp = new MultipartBody.Builder().
        setType(MultipartBody.FORM).
        addFormDataPart("f", file.getName, RequestBody.create(MediaType.parse("text/plain"), file)).
        build()
      val req = new Request.Builder().method("PUT", mp).url(host / "disk-upload").build()

      http(req).as_string must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
  }
}
