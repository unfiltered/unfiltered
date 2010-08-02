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
    type ParamBind[E] = String => QueryBuilder[E,String]
    class MappedQuery[E,R](params:Map, f: ParamBind[E] => Query[E,()=>R]) {
      def orElse(ef: Seq[E] => R) = 
        f(n => new QueryBuilder(Right(params(n)))).value.fold(ef, _())
    }
    def apply[E](params: Map) = new {
      // not curried so that E can be explicit, R implicit
      def apply[R](f: Query.ParamBind[E] => Query[E,()=>R]) =
        new MappedQuery(params, f)
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
    def required(msg: E) = new Query(value.fold(
      msg => Left(msg :: Nil),
      v => v.firstOption.map { v => Right(v) } getOrElse Left(msg :: Nil)
    ))
    def optional = new Query(value.fold(
      msg => Left(msg :: Nil),
      v => Right(v.firstOption)
    ))
    def multiple = new Query(value.fold(
      msg => Left(msg :: Nil),
      v => Right(v)
    ))
  }

  val first = new Chained({ seq: Seq[String] => seq.headOption })

  def trimmed(in: String) = Some(in) map { _.trim }
  def nonempty(in: String) = Some(in) filter { ! _.isEmpty  }

  def int(in: String) =
    try { Some(in) map { _.toInt } } catch { case _ => None }

  class Query[E,A](val value: Either[List[E],A]) {
    /**
     * Joins errors into a list on the way *out* of a for exp.
     */
    def flatMap[B](f: Query[E,A] => Query[E,B]) = 
      new Query(value.fold(
        l => Left(l ::: f(this).value.left.getOrElse(Nil)),
        _ => f(this).value 
      ))
    /**
     * Maps the yield into a function, that won't be evaluated
     * unless the top Either is still a Right.
     */
    def map(f: Query[E,A] => ResponseFunction) = 
      new Query(value.right.map { _ => () => f(this) })
    /** Shortcut to getting the Right value, safe to use in yield. */
    def get = value.right.get
  }
}
