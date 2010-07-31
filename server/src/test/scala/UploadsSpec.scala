package unfiltered.request

import org.specs._

// TODO ask nathan about adding a StringPart request to dispatch
object UploadsSpec extends Specification with unfiltered.spec.Served {
  import java.io.{File => JFile}
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  
  import dispatch._
  import dispatch.mime.Mime._
  import scala.io.Source
  import java.io.{File => JFile}
 
  class TestPlan extends unfiltered.Planify({
    case POST(UFPath("/disk-upload", MultiPartParams.Disk(params, files, _))) => files("f") match { 
      case Seq(f, _*) =>
        f.write(new JFile("upload-test-out.txt")) match {
          case Some(outFile) => {
            if(new String(Source.fromFile(outFile).toArray) == new String(f.bytes)) ResponseString(
              "wrote disk read file f named %s with content type %s with correct contents" format(f.name, f.contentType)
            )
            else ResponseString("wrote disk read file f named %s with content type %s, with differing contents" format(f.name, f.contentType))
          }
          case None => ResponseString("could not read disk read file f named %s with content type %s" format(f.name, f.contentType))
        }
      case _ =>  ResponseString("what's f?")
    }
    case POST(UFPath("/stream-upload", MultiPartParams.Streamed(params, files, _))) => files("f") match {
      case Seq(f, _*) => ResponseString("stream read file f is named %s with content type %s" format(f.name, f.contentType))
      case _ =>  ResponseString("what's f?")
    }
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "MultiPartParams.Disk" should {
    shareVariables()
    doAfter { 
      val out = new JFile("upload-test-out.txt")
      if(out.exists) out.delete
    }
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("upload-test.txt").toURI)
      file.exists must_==true
      Http(host / "disk-upload" << ("f", file, "text/plain") as_str) must_=="wrote disk read file f named upload-test.txt with content type text/plain with correct contents"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("upload-test.txt").toURI)
      file.exists must_==true
      Http(host / "stream-upload" << ("f", file, "text/plain") as_str) must_=="stream read file f is named upload-test.txt with content type text/plain"
    }
  }
}
