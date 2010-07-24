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

  abstract class Extract[T](f: Map => Option[T]) {
    def this(name: String, f: Seq[String] => Option[T]) = 
      this({ params: Map => f(params(name)) })
    def unapply(params: Map) = f(params) map {
      (_, params)
    }
  }

  object Query {
    class Builder(params: Map) {
      def apply[T](f: Map => Option[T]): Query[Option[T]] =
        {
          val value = f(params)
          new Query(() => value, !value.isEmpty)
        }
        

      def apply[T](name: String, f: Seq[String] => Option[T]): Query[Option[T]] =
        apply { params: Map => f(params(name)) }
    }
    def unapply(req: HttpServletRequest) = Params.unapply(req) map { case (params, req) =>
      (new Builder(params), req)
    }
    
  }
  
  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained({ seq: Seq[String] => seq.headOption })

  val trimmed = { (_: Option[String]) map { _.trim } }
  val nonempty = { (_: Option[String]) filter { ! _.isEmpty  } }

  val int = { s: Option[String] =>
    try { s map { _.toInt } } catch { case _ => None }
  }
  
  class Query[E](val value: () => E, val complete: Boolean) {
    def flatMap[F](f: (() => E) => Query[F]) = {
      val q = f(value)
      new Query(q.value, complete && q.complete)
    }
    def map(f: E => unfiltered.response.ResponsePackage.ResponseFunction) = 
      flatMap(v => new Query({ () => f(value()) }, complete))
    def orElse(f: => E) =
      if (complete) value()
      else f
  }
}