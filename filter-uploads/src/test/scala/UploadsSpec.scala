package unfiltered.filter.request

import org.specs2.mutable._

class UploadsSpec extends Specification with unfiltered.specs2.jetty.Served {
  import java.io.{FileInputStream => FIS}
  import org.apache.commons.io.{IOUtils => IOU}
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath, _}

  import java.io.{File => JFile}
  import java.util.Arrays

  private val directory = java.nio.file.Files.createTempDirectory(new JFile("target").toPath, "UploadsSpec").toFile

  class TestPlan(directory: JFile) extends unfiltered.filter.Plan {
    def intent = {
      case POST(UFPath("/disk-upload") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "disk read file f named %s with content type %s".format(f.name, f.contentType))
        case f =>  ResponseString("what's f?")
      }
      case POST(UFPath("/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile(directory, "1upload-test-out.txt")) match {
            case Some(outFile) =>
              if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), f.bytes)) ResponseString(
                "wrote disk read file f named %s with content type %s with correct contents".format(
                  f.name, f.contentType)
              )
              else ResponseString(
                "wrote disk read file f named %s with content type %s, with differing contents".format(
                  f.name, f.contentType))
            case None => ResponseString(
              "did not write disk read file f named %s with content type %s".format(f.name, f.contentType))
        }
        case _ =>  ResponseString("what's f?")
      }
      case POST(UFPath("/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "stream read file f is named %s with content type %s".format(
            f.name, f.contentType))
        case _ =>  ResponseString("what's f?")
      }
      case POST(UFPath("/stream-upload/write") & MultiPart(req)) =>
        MultiPartParams.Streamed(req).files("f") match {
         case Seq(f, _*) =>
            val src = IOU.toByteArray(getClass.getResourceAsStream("/upload-test.txt"))
            f.write(new JFile(directory, "2upload-test-out.txt")) match {
              case Some(outFile) =>
                if(Arrays.equals(IOU.toByteArray(new FIS(outFile)), src)) ResponseString(
                  "wrote stream read file f named %s with content type %s with correct contents".format(
                    f.name, f.contentType)
                )
                else ResponseString(
                  "wrote stream read file f named %s with content type %s, with differing contents".format(
                    f.name, f.contentType))
              case None => ResponseString(
                "did not write stream read file f named %s with content type %s".format(
                  f.name, f.contentType))
            }
          case _ =>  ResponseString("what's f?")
        }
      case POST(UFPath("/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
        case Seq(f, _*) => ResponseString(
          "memory read file f is named %s with content type %s".format(
            f.name, f.contentType))
        case _ =>  ResponseString("what's f?")
      }
      case POST(UFPath("/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
        case Seq(f, _*) =>
          f.write(new JFile(directory, "3upload-test-out.txt")) match {
            case Some(outFile) => ResponseString(
              "wrote memory read file f is named %s with content type %s".format(
                f.name, f.contentType))
            case None =>  ResponseString(
              "did not write memory read file f is named %s with content type %s".format(
                f.name, f.contentType))
          }
        case _ =>  ResponseString("what's f?")
      }
    }
  }

  def setup = { _.plan(new TestPlan(directory)) }

  "MultiPartParams" should {
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "disk-upload").<<*("f", file, "text/plain")).as_string must_== "disk read file f named upload-test.txt with content type text/plain"
    }
    "handle writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "disk-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote disk read file f named upload-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload").<<*("f", file, "text/plain")).as_string must_== "stream read file f is named upload-test.txt with content type text/plain"
    }
    "handle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "stream-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "wrote stream read file f named upload-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload").<<*("f", file, "text/plain")).as_string must_== "memory read file f is named upload-test.txt with content type text/plain"
    }
    "not write memory read files" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(req(host / "mem-upload" / "write").<<*("f", file, "text/plain")).as_string must_== "did not write memory read file f is named upload-test.txt with content type text/plain"
    }
  }
}
