package unfiltered.netty.request

import org.specs2.mutable.Specification
import unfiltered.netty.{async, cycle}
import unfiltered.request.{&, POST, Path => UFPath}
import unfiltered.response.{Html, ResponseString}
import unfiltered.specs2.netty.Served
import java.io.{File => JFile}

import okhttp3.MediaType

object MixedPlanSpec extends Specification
  with Served {

  val html = <html>
        <head><title>unfiltered file netty uploads test</title></head>
        <body>
          <p>hello</p>
        </body>
      </html>

  def setup = {
    _.plan(cycle.MultiPartDecoder {
      case POST(UFPath("/cycle/passnotfound")) =>
        cycle.MultipartPlan.Pass
      case POST(UFPath("/cycle/pass")) =>
        cycle.MultipartPlan.Pass
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
      case POST(UFPath("/cycle/mem") & MultiPart(req)) => {
        case Decode(binding) =>
          val mem =  MultiPartParams.Memory(binding)
          (mem.files("f"), mem.params("p")) match {
            case (Seq(f, _*), p) => ResponseString(
              "cycle memory read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p))
            case _ =>  ResponseString("what's f?")
          }
      }
    }).plan(cycle.MultiPartDecoder {
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
    }).plan(async.MultiPartDecoder {
      case POST(UFPath("/async/passnotfound")) =>
        async.MultipartPlan.Pass
      case POST(UFPath("/async/pass")) =>
        async.MultipartPlan.Pass
      case POST(UFPath("/async/disk") & MultiPart(req)) => {
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
      case POST(UFPath("/async/stream") & MultiPart(req)) => {
        case Decode(binding) =>
          val stream = MultiPartParams.Streamed(binding)
          (stream.files("f"), stream.params("p")) match {
            case (Seq(f, _*), p) => binding.respond(ResponseString(
              "async stream read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p)))
            case _ =>  binding.respond(ResponseString("what's f?"))
          }
      }
      case POST(UFPath("/async/mem") & MultiPart(req)) => {
        case Decode(binding) =>
          val mem = MultiPartParams.Memory(binding)
          (mem.files("f"), mem.params("p")) match {
            case (Seq(f, _*), p) => binding.respond(ResponseString(
              "async memory read file f is named %s with content type %s and param p %s" format(
                f.name, f.contentType, p)))
            case _ => binding.respond(ResponseString("what's f?"))
          }
      }
    }).plan(async.MultiPartDecoder{
      case POST(UFPath("/async/pass") & MultiPart(req)) => {
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
    }).plan(cycle.Planify{
      case POST(UFPath("/end")) & MultiPart(req) =>
        ResponseString("")
      case UFPath("/") =>
        Html(html)
    })
  }

  "Netty MultiPartDecoder cycle and async plans, when used in the same pipeline" should {
    step {
      val out = new JFile("netty-upload-test-out.txt")
      if (out.exists) out.delete
    }

    "pass GET request upstream" in {
      http(host).as_string must_== html.toString
    }

    // General

    "respond with a 404 when passing non-parameterised content type value" in {
      val code = httpx(req(host / "ignored").POST("f", MediaType.parse("application/x-www-form-urlencoded"))).code()
      code must_== 404
    }

    // Cycle

    "respond with 404 when posting to a non-existent url" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val code = httpx(req(host / "cycle" / "notexists") <<* ("f", file, "text/plain")).code()
      code must_== 404
    }

    "handle cycle file uploads disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "disk") <<* ("f", file, "text/plain")).as_string must_== "cycle disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle cycle file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "stream") <<* ("f", file, "text/plain")).as_string must_== "cycle stream read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle cycle file uploads memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "mem") <<* ("f", file, "text/plain")).as_string must_== "cycle memory read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle passed cycle file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "cycle" / "pass") <<* ("f", file, "text/plain")).as_string must_== "cycle disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "respond with a 404 when passing in a cycle plan with no matching intent" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val code = httpx(req(host / "cycle" / "passnotfound") <<* ("f", file, "text/plain")).code()
      code must_== 404
    }

    // Async

    "handle async file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "disk") <<* ("f", file, "text/plain")).as_string must_== "async disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p"
    }

    "handle async file uploads streamed" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "stream") <<* ("f", file, "text/plain")).as_string must_== "async stream read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle async file uploads memory" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "mem") <<* ("f", file, "text/plain")).as_string must_== "async memory read file f is named netty-upload-big-text-test.txt with content type text/plain and param p List()"
    }

    "handle passed async file uploads to disk" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      http(req(host / "async" / "pass") <<* ("f", file, "text/plain")).as_string must_== "async disk read file f named netty-upload-big-text-test.txt with content type text/plain and param p"
    }

    "respond with a 404 when passing in an async plan with no matching intent" in {
      val file = new JFile(getClass.getResource("/netty-upload-big-text-test.txt").toURI)
      file.exists must_==true
      val code = httpx(req(host / "async" / "passnotfound") <<* ("f", file, "text/plain")).code()
      code must_== 404
    }
  }
}
