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

  abstract class Extract[E,T](f: Map => Option[T]) {
    def this(name: String, f: Seq[String] => Option[T]) = 
      this({ params: Map => f(params(name)) })
    def unapply(params: Map) = f(params) map {
      (_, params)
    }
  }

  type QueryValue[E,T] = Either[E, T]
  object Query {
    class Builder1(params: Map) {
      def errors[E] = new {
        def apply[R](f: Builder2[E] => Query[E,R]) = new {
          def orElse(ef: E => R) =
            f(new Builder2[E](params)).value.fold(ef, identity[R])
        }
      }
    }
    class Builder2[E](params: Map){
      val first = new EitherChained({ seq: Seq[String] => Right[E,Option[String]](seq.headOption) })
      def apply[T](f: Map => Either[E, T]): Query[E,T] =
        new Query(f(params))
      def apply[T](name: String, f: Seq[String] => Either[E,T]): Query[E,T] =
        apply { params: Map => f(params(name)) }
    }
    def unapply(req: HttpServletRequest) = Params.unapply(req) map { case (params, req) =>
      (new Builder1(params), req)
    }
  }
  
  class Chained[A, B](f: A => B) extends (A => B) {
    def apply(a: A) = f(a)
    def ~> [C](that: B => C) = new Chained(this andThen that)
  }
  class EitherChained[E, A, B](f: A => Either[E,Option[B]]) extends (A => Either[E,Option[B]]) {
    def apply(a: A) = f(a)
    def opt [C] (that: Option[B] => Option[C]) =
      new EitherChained(this andThen { _.right map that })
    def err[C](that: Option[B]=> Option[C], msg: E) =
      new EitherChained(this andThen { _.right.flatMap { prev =>
        that(prev) match {
          case None => if (prev.isEmpty) Right(None) else Left(msg)
          case value => Right(value)
        }
      } })
    def orError (msg: E) = this andThen { _.right.flatMap {
      case None => Left(msg)
      case Some(value) => Right(value)
    } }
  }

  val first = new EitherChained({ seq: Seq[String] => Right(seq.headOption) })
  val firstOption = new Chained({ seq: Seq[String] => seq.headOption })

  def trimmed(in: Option[String]) = in.map { _.trim }
  def nonempty(in: Option[String]) = in.filter { ! _.isEmpty  }

  def int(in: Option[String]) =
    try { in.map { _.toInt } } catch { case _ => None }

  class Query[E,A](val value: QueryValue[E,A]) {
    def flatMap[B](f: QueryValue[E,A] => Query[E,B]) = 
      f(value)
      
    def map(f: A => ResponseFunction) = 
      new Query(value.right.map(f))
  }
}