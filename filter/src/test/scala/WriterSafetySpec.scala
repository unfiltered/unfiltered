package unfiltered.server

import javax.servlet.http.HttpServletResponse

import org.specs2.mutable._
import unfiltered.response._
import unfiltered.request._
import unfiltered.request.{Path => UFPath}
import unfiltered.filter.WritableServletResponse

object WriterSafetySpec extends Specification with unfiltered.specs2.jetty.Served {

  case class WriteString(text: String) extends Responder[HttpServletResponse] {
    override def respond(res: HttpResponse[HttpServletResponse]) {
      val writer = WritableServletResponse(res).getWriter
      writer.print(text)
      writer.close()
    }
  }

  def setup = _.filter(unfiltered.filter.Planify {
    case GET(UFPath("/writer")) => WriteString("written") ~> Ok
  })

  "An Responder[HttpServletResponse]" should {
    "have reliable access to the underlying writer" in {
      http(host / "writer" as_str) must_== "written"
    }
  }
}
