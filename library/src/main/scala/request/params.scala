package unfiltered.request

import unfiltered.response.ResponsePackage.ResponseFunction
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

  type QueryValue[E,T] = Either[E, () => T]
  object Query {
    class Builder1(params: Map) {
      def errors[E] = new {
        def apply[R](f: Builder2[E] => Query[E,R]) = new {
          def orElse(ef: E => R) =
            f(new Builder2[E](params)).value.fold(ef, v => v())
        }
      }
    }
    class Builder2[E](params: Map){
      def apply[T](f: Map => Either[E, T]): Query[E,T] =
        new Query(f(params).right.map { v => () => v })
      def apply[T](name: String, f: Seq[String] => Either[E,T]): Query[E,T] =
        apply { params: Map => f(params(name)) }
    }
    def unapply(req: HttpServletRequest) = Params.unapply(req) map { case (params, req) =>
      (new Builder1(params), req)
    }
  }
  
  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained({ seq: Seq[String] => Right(seq.headOption) })
  
  def trimmed[E](in: Either[E,Option[String]]) = in.right map { _.map { _.trim } }
  def nonempty[E](in: Either[E,Option[String]]) = in.right map { _.filter { ! _.isEmpty  } }

  def int(in: Option[String]) =
    try { in.map { _.toInt } } catch { case _ => None }

  def err[E,A,B](f: Option[A]=> Option[B], msg: E)(in: Either[E,Option[A]]) =
    in.right.flatMap { prev =>
      f(prev) match {
        case None => if (prev.isEmpty) Right(None) else Left(msg)
        case value => Right(value)
      }
    }
  
  def opt[E,A,B](f: Option[A]=> Option[B])(in: Either[E,Option[A]]) =
    in.right.map(f)

  def require[E,T](error: E)(in: Either[E,Option[T]]) = in.right flatMap {
    case None => Left(error)
    case Some(value) => Right(value)
  }

  class Query[E,A](val value: QueryValue[E,A]) {
    def flatMap[B](f: QueryValue[E,A] => Query[E,B]) = 
      f(value)
      
    def map(f: A => ResponseFunction) = 
      new Query(value.right.map{ v => () => f(v()) })
  }
}