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
  
  class TestPlan extends unfiltered.Planify({
    // !IMPORTANT! MultiPartParams can not be used in first case
    // it will get called twice but will only parse out the request
    // body the first time
    case POST(UFPath("/disk-upload", r)) => r match { 
      case MultiPartParams.Disk(params, files, _) =>  files("f") match {
        case Seq(f, _*) => ResponseString("disk read file f is named %s with content type %s" format(f.name, f.contentType))
        case _ =>  ResponseString("what's f?")
      }
    }
    case POST(UFPath("/stream-upload", r)) => r match { 
      case MultiPartParams.Streamed(params, files, _) =>  files("f") match {
        case Seq(f, _*) => ResponseString("stream read file f is named %s with content type %s" format(f.name, f.contentType))
        case _ =>  ResponseString("what's f?")
      }
    }
  })
  
  def setup = { _.filter(new TestPlan) }
  
  "MultiPartParams.Disk" should {
    shareVariables()
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("upload-test.txt").toURI)
      file.exists must_==true
      Http(host / "disk-upload" << ("f", file, "text/plain") as_str) must_=="disk read file f is named upload-test.txt with content type text/plain"
    }
    "handle file uploads streamed" in {
      val file = new JFile(getClass.getResource("upload-test.txt").toURI)
      file.exists must_==true
      Http(host / "stream-upload" << ("f", file, "text/plain") as_str) must_=="stream read file f is named upload-test.txt with content type text/plain"
    }
  }
}
