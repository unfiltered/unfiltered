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

  object Query {
    class Builder[E](params: Map) {
      def apply(name: String) =
        new QueryBuilder[E,String](Right(params(name)))
    }
    def apply[E](params: Map) = new {
      def apply[R](f: Query.Builder[E] => Query[E,R]) =
        new {
          def orElse(ef: E => R) = 
            f(new Query.Builder(params)).value.fold(ef, identity[R])
        }
    }
  }
  
  class Chained[A, B](f: A => B) extends (A => B) {
    def apply(a: A) = f(a)
    def ~> [C](that: B => C) = new Chained(this andThen that)
  }

  type Condition[A,B] = A => Option[B]
  class QueryBuilder[E, A](value: Either[E,Seq[A]]) extends {
    def is [B](cond: Condition[A,B]) = new QueryBuilder(
      value.right.map { _.flatMap { i => cond(i).toList } }
    )
    def is [B](cond: Condition[A,B], msg: E) = new QueryBuilder(
      value.right.flatMap { seq =>
        val s: Either[E, List[B]] = Right(List.empty[B])
        (s /: seq) { (either, item) =>
          either.right.flatMap { l =>
            cond(item).map { i => Right(i :: l) } getOrElse Left(msg)
          }
        }
      }
    )
    def required(msg: E) = new Query(value.right.flatMap {
      _.firstOption.map { v => Right(v) } getOrElse Left(msg)
    })
    def optional = new Query(value.right.map { _.firstOption })
    def multiple = new Query(value)
  }

  val first = new Chained({ seq: Seq[String] => seq.headOption })

  def trimmed(in: Option[String]) = in map { _.trim }
  def nonempty(in: Option[String]) = in filter { ! _.isEmpty  }

  def int(in: String) =
    try { Some(in) map { _.toInt } } catch { case _ => None }

  class Query[E,A](val value: Either[E,A]) {
    def flatMap[B](f: Query[E,A] => Query[E,B]) = 
      new Query(value.right.flatMap { _ => f(this).value })
      
    def map(f: Query[E,A] => ResponseFunction) = 
      new Query(value.right.map { v => f(this) })
    def get = value.right.get
  }
}
