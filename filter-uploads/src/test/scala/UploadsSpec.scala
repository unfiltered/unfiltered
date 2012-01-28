package unfiltered.filter.request

import org.specs._

object UploadsSpec extends Specification with unfiltered.spec.jetty.Served {
  import java.io.{File => JFile,FileInputStream => FIS}
  import org.apache.commons.io.{IOUtils => IOU}
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath, _}

  import dispatch._
  import dispatch.mime.Mime._
  import scala.io.Source
  import java.io.{File => JFile}

  class TestPlan extends unfiltered.filter.Planify({
    case POST(UFPath("/disk-upload") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
      case Seq(f, _*) => ResponseString(
        "disk read file f named %s with content type %s" format(f.name, f.contentType))
      case f =>  ResponseString("what's f?")
    }
    case POST(UFPath("/disk-upload/write") & MultiPart(req)) => MultiPartParams.Disk(req).files("f") match {
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
            "did not write disk read file f named %s with content type %s" format(f.name, f.contentType))
      }
      case _ =>  ResponseString("what's f?")
    }
    case POST(UFPath("/stream-upload") & MultiPart(req)) => MultiPartParams.Streamed(req).files("f") match {
      case Seq(f, _*) => ResponseString(
        "stream read file f is named %s with content type %s" format(
          f.name, f.contentType))
      case _ =>  ResponseString("what's f?")
    }
    case POST(UFPath("/stream-upload/write") & MultiPart(req)) =>
      MultiPartParams.Streamed(req).files("f") match {
       case Seq(f, _*) =>
          val src = IOU.toString(getClass.getResourceAsStream("/upload-test.txt"))
          f.write(new JFile("upload-test-out.txt")) match {
            case Some(outFile) =>
              if(IOU.toString(new FIS(outFile)) == src) ResponseString(
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
        case _ =>  ResponseString("what's f?")
      }
    case POST(UFPath("/mem-upload") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
      case Seq(f, _*) => ResponseString(
        "memory read file f is named %s with content type %s" format(
          f.name, f.contentType))
      case _ =>  ResponseString("what's f?")
    }
    case POST(UFPath("/mem-upload/write") & MultiPart(req)) => MultiPartParams.Memory(req).files("f") match {
      case Seq(f, _*) =>
        f.write(new JFile("upload-test-out.txt")) match {
          case Some(outFile) => ResponseString(
            "wrote memory read file f is named %s with content type %s" format(
              f.name, f.contentType))
          case None =>  ResponseString(
            "did not write memory read file f is named %s with content type %s" format(
              f.name, f.contentType))
        }
      case _ =>  ResponseString("what's f?")
    }
  })

  def setup = { _.filter(new TestPlan) }

  "MultiPartParams" should {
    shareVariables()
    doBefore {
      val out = new JFile("upload-test-out.txt")
      if(out.exists) out.delete
    }
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "disk-upload" <<* ("f", file, "text/plain") as_str) must_=="disk read file f named upload-test.txt with content type text/plain"
    }
    "handle writing file uploads written to disk" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "disk-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote disk read file f named upload-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "stream-upload" <<* ("f", file, "text/plain") as_str) must_=="stream read file f is named upload-test.txt with content type text/plain"
    }
    "handle writing file uploads streamed" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "stream-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="wrote stream read file f named upload-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads all in memory" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "mem-upload" <<* ("f", file, "text/plain") as_str) must_=="memory read file f is named upload-test.txt with content type text/plain"
    }
    "not write memory read files" in {
      val file = new JFile(getClass.getResource("/upload-test.txt").toURI)
      file.exists must_==true
      http(host / "mem-upload" / "write" <<* ("f", file, "text/plain") as_str) must_=="did not write memory read file f is named upload-test.txt with content type text/plain"
    }
  }
}
