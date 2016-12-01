package unfiltered.netty.request

import org.specs2.mutable.Specification

import unfiltered.netty.cycle
import unfiltered.request.{ Path => UFPath, POST, & }
import unfiltered.response.{ NotFound, ResponseString }
import unfiltered.specs2.netty.Served

import java.io.{ File => JFile,FileInputStream => FIS }

import org.apache.commons.io.{ IOUtils => IOU }

object CycleUploadSpec extends Specification
  with Served {

  def setup = {
    val plan = cycle.MultiPartDecoder({
      case POST(UFPath("/disk-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
          case Seq(f, _*) => ResponseString(
            "disk read file f named %s with content type %s" format(
              f.name, f.contentType))
          case f => ResponseString("what's f?")
        }
      }
      case POST(UFPath("/disk-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Disk(binding).files("f") match {
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
        }
      case POST(UFPath("/stream-upload") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
            case Seq(f, _*) => ResponseString(
              "stream read file f is named %s with content type %s" format(
                f.name, f.contentType))
            case _ => ResponseString("what's f?")
          }
      }
      case POST(UFPath("/stream-upload/write") & MultiPart(req)) => {
        case Decode(binding) =>
          MultiPartParams.Streamed(binding).files("f") match {
           case Seq(f, _*) =>
              val src = IOU.toString(getClass.getResourceAsStream("/netty-upload-big-text-test.txt"))
              f.write(new JFile("upload-test-out.txt")) match {
                case Some(outFile) =>
                  if (IOU.toString(new FIS(outFile)) == src) ResponseString(
                    "wrote stream read file f named %s with content type %s with correct contents" format(
                      f.name, f.contentType)
                  )
                  else ResponseString(
                    "wrote stream read file f named %s with content type %s, with differing contents" format(
                      f.name, f.contentType))
                case None => ResponseString(
                  "did not write stream read file f named %s with content type %s" format(
                    f.name, f.contentType))
              }
            case _ => ResponseString("what's f?")
          }
        }
        case POST(UFPath("/mem-upload") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
              case Seq(f, _*) => ResponseString(
                "memory read file f is named %s with content type %s" format(
                  f.name, f.contentType))
              case _ => ResponseString("what's f?")
            }
        }
        case POST(UFPath("/mem-upload/write") & MultiPart(req)) => {
          case Decode(binding) =>
            MultiPartParams.Memory(binding).files("f") match {
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
        }
    })
    _.plan(plan).plan(cycle.Planify {
      case _ => NotFound
    })
  }

  "Netty cycle.MultiPartDecoder" should {
    step {
      val out = new JFile("netty-upload-test-out.txt")
      if (out.exists) out.delete
    }
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "disk-upload") <<* ("f", file, "text/plain")).as_string must_== "disk read file f named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload") <<* ("f", file, "text/plain")).as_string must_== "stream read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "handle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload" / "write") <<* ("f", file, "text/plain")).as_string must_== "wrote stream read file f named netty-upload-big-text-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload") <<* ("f", file, "text/plain")).as_string must_== "memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "not write memory read files" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload" / "write") <<* ("f", file, "text/plain")).as_string must_== "did not write memory read file f is named netty-upload-big-text-test.txt with content type text/plain"
    }
    "respond with a 404" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val resp = httpx(req(host / "notfound") <<* ("f", file, "text/plain"))
      resp.code must_== 404
    }
  }
}
