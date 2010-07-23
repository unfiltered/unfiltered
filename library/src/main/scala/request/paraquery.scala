package unfiltered.request

import unfiltered.response.{Responder,ResponseString}
import javax.servlet.http.HttpServletRequest

class Query[E](val value: E, val complete: Boolean) {
  def flatMap[F](f: E => Query[F]) = {
    val q = f(value)
    new Query(q.value, complete && q.complete)
  }
  def map[F](f: E => F) = flatMap(v => new Query(f(value), complete))
  def orElse[F <: E](f: => F) =
    if (complete) value
    else f
}

object Query {
  def apply[T](params: Params.ParamMap, name: String, f: Seq[String] => Option[T]) =
    new Query(f(params(name)), f(params(name)).isEmpty)
}

object Test {
  def q[T](name: String, f: Seq[String] => Option[T]) = Query(Map.empty, name, f)
  val r: Responder = (for {
    name <- q("name", Params.first ~> Params.trimmed ~> Params.nonempty)
    even <- q("even", Params.first ~> Params.int ~> { _ filter { _ % 2 == 0 } })
  } yield {
      ResponseString(name.get)
  }) orElse {
    ResponseString("oops")
  }
}