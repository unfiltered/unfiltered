package unfiltered.request

import javax.servlet.http.HttpServletRequest

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  type Map = scala.collection.Map[String, Seq[String]]
  /**
    Given a req, extract the request params into a (Map[String, Seq[String]], request).
    The Map is assigned a default value of Nil, so param("p") would return Nil if there
    is no such parameter, or (as normal for servlets) a single empty string if the
    parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) => 
        m + (n -> req.getParameterValues(n))
      )).withDefaultValue(Nil), req)
  }

  abstract class Extract[T](name: String, f: Seq[String] => Option[T]) {
    def unapply(params: Map) = f(params(name)) map {
      (_, params)
    }
  }

  object Query {
    class Builder(params: Map) {
      def apply[T](name: String, f: Seq[String] => Option[T]) =
       new Query(f(params(name)), f(params(name)).isEmpty)
    }
    def unapply(req: HttpServletRequest) = Params.unapply(req) map { case (params, req) =>
      (new Builder(params), req)
    }
    
  }
  
  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained( { seq: Seq[String] => seq.headOption } )

  val trimmed = { (_: Option[String]) map { _.trim } }
  val nonempty = { (_: Option[String]) filter { ! _.isEmpty  } }

  val int = { s: Option[String] =>
    try { s map { _.toInt } } catch { case _ => None }
  }
  
  class Query[E](val value: E, val complete: Boolean) {
    def flatMap[F](f: E => Query[F]) = {
      val q = f(value)
      new Query(q.value, complete && q.complete)
    }
    def map[F <: unfiltered.response.ResponsePackage.ResponseFunction](f: E => F) = 
      flatMap(v => new Query(
        if (complete) f(value)
        else unfiltered.response.Pass, 
        complete))
    def orElse(f: => E) =
      if (complete) value
      else f
  }
}
object Test {
  import unfiltered.response.{ResponsePackage,ResponseString}
  val q = new Params.Query.Builder(Map.empty)
  val r: ResponsePackage.ResponseFunction = ( for {
    name <- q("name", Params.first ~> Params.trimmed ~> Params.nonempty)
    even <- q("even", Params.first ~> Params.int ~> { _ filter { _ % 2 == 0 } })
  } yield ResponseString(name.get) ) orElse {
    ResponseString("oops")
  }
}