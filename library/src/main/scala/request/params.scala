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
    /** @return a query binding function for the given parameters */
    def apply[E](params: Map) = new {
      // not curried so that E can be explicit, R implicit
      def apply[R](f: Query.ParamBind[E] => Query[E,()=>R]) =
        new MappedQuery(params, f)
    }
  }

  type Condition[A,B] = Option[A] => Option[B]

  class Chained[B](f: Seq[String] => Option[B]) extends (Seq[String] => Option[B]) {
    def apply(a: Seq[String]) = f(a)
    def ~> [C](that: Option[B] => Option[C]) = new Chained(f andThen that)
  }

  class QueryBuilder[E, A](value: Either[E,Seq[A]]) extends {
    def is [B](cond: Condition[A,B]) = new QueryBuilder(
      value.right.map { _.flatMap { i => cond(Some(i)).toList } }
    )
    def is [B](cond: Condition[A,B], msg: E) = new QueryBuilder(
      value.right.flatMap { seq =>
        val s: Either[E, List[B]] = Right(Nil)
        (s /: seq) { (either, item) =>
          either.right.flatMap { l =>
            cond(Some(item)).map { i => Right(i :: l) } getOrElse Left(msg)
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

  def first = new (Seq[String] => Option[String]) {
    def apply(seq: Seq[String]) = seq.firstOption
    def ~> [B] (that: Condition[String, B]) = new Chained({seq =>
      that(this(seq))
    })
  }

  val trimmed = (_: Option[String]) map { _.trim }
  val nonempty = (_: Option[String]) filter { ! _.isEmpty  }

  def int(v: Option[String]) =
    try { v map { _.toInt } } catch { case _ => None }

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
     * Maps the yield into a function, it won't be evaluated
     * unless the top Either is still a Right.
     */
    def map(f: Query[E,A] => ResponseFunction) =
      new Query(value.right.map { _ => () => f(this) })
    /** Shortcut to getting the Right value, safe to use in yield. */
    def get = value.right.get
  }
}
