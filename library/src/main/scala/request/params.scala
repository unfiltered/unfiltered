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

  abstract class Extract[E,T](f: Map => Either[E,T]) {
    def this(name: String, f: Seq[String] => Either[E,T]) = 
      this({ params: Map => f(params(name)) })
    def unapply(params: Map) = f(params).right.toOption map {
      (_, params)
    }
  }

  object Query {
    class Builder(params: Map) {
      def apply[E,T](f: Map => Either[E, T]): Query[Either[E, T]] =
        {
          val value = f(params)
          new Query(() => value, value.isRight)
        }

      def apply[E,T](name: String, f: Seq[String] => Either[E,T]): Query[Either[E,T]] =
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

  val first = new Chained({ seq: Seq[String] => Right(seq.headOption) })

  def trimmed[E](in: Either[E,Option[String]]) = in.right map { _.map { _.trim } }
  def nonempty[E](in: Either[E,Option[String]]) = in.right map { _.filter { ! _.isEmpty  } }

  def int[E](in: Either[E,Option[String]]) = in match {
    case Left(error) => Left(error)
    case Right(value) => 
      Right(try { value.map { _.toInt } } catch { case _ => None })
  }
  
  def require[E,T](error: E)(in: Either[E,Option[T]]) = in.right flatMap {
    case None => Left(error)
    case Some(value) => Right(value)
  }
  
  class Query[A](val value: () => A, val accept: Boolean) {
    def flatMap[B](f: (() => A) => Query[B]) = {
      val q = f(value)
      new Query(q.value, accept && q.accept)
    }
    def map(f: A => unfiltered.response.ResponsePackage.ResponseFunction) = 
      flatMap(v => new Query({ () => f(value()) }, accept))
    def orElse(f: => A) =
      if (accept) value()
      else f
  }
}