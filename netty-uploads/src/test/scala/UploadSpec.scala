package unfiltered.netty.uploads

import org.specs._

object UploadSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.response._
  import unfiltered.request.{POST, &, Path => UFPath}
  import unfiltered.netty
  import unfiltered.netty.{Http => NHttp}

  import dispatch._
  import dispatch.mime.Mime._
  import scala.io.Source
  import java.io.{File => JFile}

  import org.jboss.netty.handler.codec.http.HttpChunkAggregator

  def setup = {
    val maxContentLength = 5242880 // 5MB  /* 65536 // 64KB */

    _.handler(new HttpChunkAggregator(maxContentLength))
    .handler(planify {
      case UFPath("/a") => ResponseString("http response a")
    })
    .handler(netty.cycle.Planify({
      case POST(UFPath("/b") & MultiPart(req)) => 
        ResponseString("http response")
      /*
        MultiPartParams.Disk(req).files("f") match {
        case Seq(f, _*) => ResponseString("disk read file f named %s with content type %s" format(f.name, f.contentType))
        case f =>  ResponseString("what's f?")
      }*/
      /*{
        case Open(s) => s.send("socket opened b")
        case Message(s, Text(msg)) => s.send(msg)
      }*/
    }))//.onPass(_.sendUpstream(_)))
    /*
    .handler(netty.cycle.Planify {
      case UFPath("/b") => ResponseString("http response b")
    })
    */
  }

  "A file upload" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }
    /*
    "not block standard http requests" in {
      http(host / "b" << ("body") as_str) must_==("200 OK")
      http(host / "a" as_str) must_==("http response a")
      http(host / "b" as_str) must_==("http response b")
    }
    */
    "handle file uploads written to disk" in {
      val file = new JFile(getClass.getResource("netty-upload-test.txt").toURI)
      file.exists must_==true
      http(host / "b" <<* ("f", file, "text/plain") as_str) must_=="disk read file f named upload-test.txt with content type text/plain"
    }
  }
}