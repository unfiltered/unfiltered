package unfiltered.demo

import unfiltered.request._
import unfiltered.response._

object AId extends scala.util.matching.Regex("""/a/(\d+)""")
object Id {
  def unapply(str: String) =
    try { Some(str.toInt) } 
    catch { case _ => None }
}

trait Rendering {
  def render(body: String) = template(body)
  def render(body: scala.xml.NodeSeq) = template(body.toString)
  private def template(body: String) = Html(
    <html><head><title>unfiltered demo</title></head><body> { body } </body></html>
  )
}

object Demo extends Rendering

class PlannedDemo extends unfiltered.Planify ({
  case GET(Path("/", req)) => Status(201) ~> ContentType("text/plain") ~> ResponseString("hello world")
  case GET(Path(AId(id), req)) => Demo.render(id)
  case GET(Path(Seg("b" :: Id(id) :: Nil), req)) => Demo.render(id.toString)
  case GET(Path(Seg("c" :: "d" :: what :: Nil), req)) => Demo.render(what)
  case GET(Path(Seg("e" :: Nil), Params(params, req))) => params("what") match {
    case Some(whats) => Demo.render("""%s values of `what` the first being %s """ format(whats.size, params.first("what").get))
    case _ => Demo.render(
      <p>enter a value for <strong>what</strong></p>
      <form action="/e" method="get"><input type="text" name="what"/></form>
    )
  }
  case GET(Path(Seg("f" :: Nil), Params(params, req))) => Demo.render(
    "what => %s" format(params.first("what").getOrElse("default"))
  )
})

class PlanDemo extends unfiltered.Plan with Rendering {
  def filter = {
    case GET(Path(Seg("e" :: what :: Nil), req)) => render(what)
  }
}

object DemoServer {
  def main(args: Array[String]) {
    unfiltered.server.Http(8080).filter(new PlannedDemo).filter(new PlanDemo).start()
  }
}
