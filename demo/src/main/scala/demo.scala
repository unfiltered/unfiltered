package unfiltered.demo

import unfiltered.request._
import unfiltered.response._

object AId extends scala.util.matching.Regex("""/a/(\d+)""")
object Id {
  def unapply(str: String) =
    try { Some(str.toInt) } 
    catch { case _ => None }
}

object Name extends Params.Extract("name", Params.firstOption ~> Params.trimmed ~> Params.nonempty)
object Even extends Params.Extract("even", Params.firstOption ~> Params.int ~> { _ filter { _ % 2 == 0 } })

trait Rendering {
  def render(body: String): Html = render(scala.xml.Text(body))
  def render(body: scala.xml.NodeSeq): Html = Html(
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
    case whats @ Seq(f, _*) => 
      Demo.render("""%s values of `what` the first being '%s' """ format(whats.size, f))
    case _ => Demo.render(
      <p>enter a value for <strong>what</strong></p>
      <form action="/e" method="get"><input type="text" name="what"/></form>
    )
  }
  case GET(Path(Seg("f" :: Nil), Params(params, req))) => Demo.render(
    "what => %s" format(params("what").headOption.getOrElse("default"))
  )
  case GET(Path(Seg("g" :: Nil), Params(Name(name, params), req))) => Demo.render(
    "name => '%s'" format(name)
  )
  case GET(Path(Seg("h" :: Nil), Params(Even(num, params), req))) => Demo.render(
    "your even number => %s" format(num)
  )
})

class PlanDemo extends unfiltered.Plan with Rendering {
  def filter = {
    case GET(Path(Seg(what :: Nil), req)) => render(what)
  }
}

object DemoServer {
  def main(args: Array[String]) {
    unfiltered.server.Http(8080).filter(new PlannedDemo).context("/2") { _.filter(new PlanDemo) }.run()
  }
}
