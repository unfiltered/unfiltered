package unfiltered.netty.request
import org.specs._

object MixedPlanSpec extends Specification
  with unfiltered.spec.netty.Served {

  import unfiltered.request._
  import unfiltered.request.{Path => UFPath, _}
  import unfiltered.response._
  import unfiltered.netty._

  import dispatch._

  import dispatch.mime.Mime._
  import java.io.{File => JFile,FileInputStream => FIS}
  import org.apache.commons.io.{IOUtils => IOU}

  val html = <html>
        <head><title>unfiltered file netty uploads test</title></head>
        <body>
          <p>hello</p>
        </body>
      </html>

  def setup = {
    _.handler(cycle.MultiPartDecoder {
      case POST(UFPath("/cycle/passnotfound")) => cycle.MultipartPlan.Pass
      case POST(UFPath("/cycle/pass")) => cycle.MultipartPlan.Pass
      case POST(UFPath("/cycle/disk") & MultiPart(req)) => {
        case Decode(binding) =>
          val disk = MultiPartParams.Disk(binding)
          (disk.files("f"), disk.params("p")) match {
            case (Seq(f, _*), p) =>
              ResponseString(
                "cycle disk read file f named %s with content type %s and param p %s" format(
                  f.name, f.contentType, p))
            case _ => ResponseString("what's f?")
        }
      }
      case POST(UFPath("/cycle/stream") & MultiPart(req)) => {
        case Decode(binding) =>
          val stream = MultiPartParams.Streamed(binding)
          (stream.files("f"), stream.params("p")) match {
            case (Seq(f, _*), p) => ResponseString(
              "cycle stream read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p))
            case _ =>  ResponseString("what's f?")
          }
      }
      case r@POST(UFPath("/cycle/mem") & MultiPart(req)) => {
        case Decode(binding) =>
          val mem =  MultiPartParams.Memory(binding)
          (mem.files("f"), mem.params("p")) match {
            case (Seq(f, _*), p) => ResponseString(
              "cycle memory read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p))
            case _ =>  ResponseString("what's f?")
          }
      }
    }).handler(cycle.MultiPartDecoder {
      case POST(UFPath("/cycle/pass")) & MultiPart(req) => {
        case Decode(binding) =>
          val disk = MultiPartParams.Disk(binding)
          (disk.files("f"), disk.params("p")) match {
            case (Seq(f, _*), p) =>
              ResponseString(
                "cycle disk read file f named %s with content type %s and param p %s" format(
                  f.name, f.contentType, p))
            case _ => ResponseString("what's f?")
        }
      }
    })
    .handler(async.MultiPartDecoder{
      case r @ POST(UFPath("/async/passnotfound")) => async.MultipartPlan.Pass
      case r @ POST(UFPath("/async/pass")) => async.MultipartPlan.Pass
      case r @ POST(UFPath("/async/disk") & MultiPart(req)) => {
        case Decode(binding) =>
          val disk = MultiPartParams.Disk(binding)
          (disk.files("f"), disk.params("p")) match {
            case (Seq(f, _*), p) =>
              binding.respond(ResponseString(
                "async disk read file f named %s with content type %s and param p" format(
                  f.name, f.contentType, p)))
              case _ =>  binding.respond(ResponseString("what's f?"))
            }
      }
      case r @ POST(UFPath("/async/stream") & MultiPart(req)) => {
        case Decode(binding) =>
          val stream = MultiPartParams.Streamed(binding)
          (stream.files("f"), stream.params("p")) match {
            case (Seq(f, _*), p) => binding.respond(ResponseString(
              "async stream read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p)))
            case _ =>  binding.respond(ResponseString("what's f?"))
          }
      }
      case r @ POST(UFPath("/async/mem") & MultiPart(req)) => {
        case Decode(binding) =>
          val mem = MultiPartParams.Memory(binding)
          (mem.files("f"), mem.params("p")) match {
            case (Seq(f, _*), p) => binding.respond(ResponseString(
              "async memory read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p)))
            case _ => binding.respond(ResponseString("what's f?"))
          }
      }
    }).handler(async.MultiPartDecoder{
      case r @ POST(UFPath("/async/pass") & MultiPart(req)) => {
        case Decode(binding) =>
          val disk = MultiPartParams.Disk(binding)
          (disk.files("f"), disk.params("p")) match {
            case (Seq(f, _*), p) =>
              binding.respond(ResponseString(
                "async disk read file f named %s with content type %s and param p" format(
                  f.name, f.contentType, p)))
            case _ => binding.respond(ResponseString("what's f?"))
          }
      }
    }).handler(cycle.Planify{
      case POST(UFPath("/end")) & MultiPart(req) => ResponseString("")
      case UFPath("/") => Html(html)
    })
  }

  "Netty MultiPartDecoder cycle and async plans, when used in the same pipeline" should {
    shareVariables()
    doBefore {
      val out = new JFile("netty-upload-test-out.txt")
      if(out.exists) out.delete
    }

    "pass GET request upstream" in {
      http(host as_str) must_== html.toString
    }

    /** Cycle */

    "respond with 404 when posting to a non-existent url" in {
      val http = new dispatch.Http
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "cycle" / "notexists" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }

    "handle cycle file uploads disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "disk" <<* ("f", file, "text/plain") as_str) must_=="cycle disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle cycle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "stream" <<* ("f", file, "text/plain") as_str) must_=="cycle stream read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }
    "handle cycle file uploads memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "mem" <<* ("f", file, "text/plain") as_str) must_=="cycle memory read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }
    "handle passed cycle file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "cycle" / "pass" <<* ("f", file, "text/plain") as_str) must_=="cycle disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }
    
    "respond with a 404 when passing in a cycle plan with no matching intent" in {
      val http = new dispatch.Http
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "cycle" / "passnotfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }

    /** Async */

    "handle async file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "disk" <<* ("f", file, "text/plain") as_str) must_=="async disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p"
    }
    
    "handle async file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "stream" <<* ("f", file, "text/plain") as_str) must_=="async stream read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }
    
    "handle async file uploads memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "mem" <<* ("f", file, "text/plain") as_str) must_=="async memory read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }
    
    "handle passed async file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(host / "async" / "pass" <<* ("f", file, "text/plain") as_str) must_=="async disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p"
    }

    "respond with a 404 when passing in an async plan with no matching intent" in {
      val http = new dispatch.Http
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      try {
        http x (host / "async" / "passnotfound" <<* ("f", file, "text/plain") >| ) {
          case (code,_,_,_) =>
            code must_== 404
        }
      } finally { http.shutdown }
    }
  }
}
