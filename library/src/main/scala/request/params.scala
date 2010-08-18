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
   * Given a req, extract the request params into a (Map[String, Seq[String]], request).
   * The Map is assigned a default value of Nil, so param("p") would return Nil if there
   * is no such parameter, or (as normal for servlets) a single empty string if the
   * parameter was supplied without a value. */
  def unapply(req: HttpServletRequest) = {
    val names = JEnumerationIterator[String](req.getParameterNames.asInstanceOf[java.util.Enumeration[String]])
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) =>
        m + (n -> req.getParameterValues(n))
      )).withDefaultValue(Nil), req)
  }

  /**
   * A function Seq[String] => Option[B]  used to test and transform values
   * from the parameter map. Conditions may be chained with `~>` */
  class ParamMapper[B](f: Seq[String] => Option[B]) extends (Seq[String] => Option[B]) {
    def apply(a: Seq[String]) = f(a)
    def ~> [C](that: Option[B] => Option[C]) = new ParamMapper({ f andThen that })
  }

  /** Maps first parameter, if present. */
  val first = new ParamMapper(_.firstOption)

  /**
   * Base class for parameter extractor objects, may be extended inline with
   * chained ParamMapper objects. */
  abstract class Extract[E,T](f: Map => Option[T]) {
    def this(name: String, f: Seq[String] => Option[T]) =
      this({ params: Map => f(params(name)) })
    def unapply(params: Map) = f(params) map {
      (_, params)
    }
  }

  type Log[E] = List[(String,E)]
  type QueryFn[E,A] = (Map, Option[String], Log[E]) =>
    (Option[String], Log[E], A)
  type QueryResult[E,A] = Either[Log[E], A]

  /* Implicitly provide 'orElse' for QueryResult (either) type. */
  case class QueryResultX[E,A](r: QueryResult[E,A]) {
    def orElse(handler: Log[E] => A) =
      r.left.map(handler).merge
  }
  implicit def queryOrElse[E,A](r: QueryResult[E,A]): QueryResultX[E,A] =
    QueryResultX(r)

  /* The query-building monad, a variant of state-transformer. */
  case class QueryM[E,A](exec: QueryFn[E,A]) {
    def flatMap[B](f: A => QueryM[E,B]): QueryM[E,B] =
      QueryM {
        (params, key0, log0) =>
          val (key1, log1, value) = exec(params, key0, log0)
          f(value).exec(params, key1, log1)
      }

    /* Compose monads, ignoring value of this. */
    def andThen[B](that: QueryM[E,B]): QueryM[E,B] =
      flatMap(_ => that)

    /* This map is not defined as flatMap(unit(f(_))), as expected.
       If any errors have been accumulated, it does NOT run the
       query function. */
    def map[B](f: A => B): QueryM[E,QueryResult[E,B]] =
      QueryM {
        (params, key0, log0) =>
          val (key1, log1, value) = exec(params, key0, log0)
          (key1, log1, log1 match {
            case Nil => Right(f(value))
            case _ => Left(log1.reverse)
          })
      }

    /* Combinator for filtering the value and tagging errors. */
    def is[B](f: A => Option[B], err: E): QueryM[E,Option[B]] =
      QueryM {
        (params, key0, log0) =>
          val (key1, log1, value) = exec(params, key0, log0)
          key1 match {
            case None => (key1, log1, None) // do not record error
            case Some(k) =>
              f(value) match {
                case None => (None, (k,err)::log1, None) // do record
                case Some(v) => (key1, log1, Some(v))
              }
          }
      }

    def apply(params: Map) = exec(params, None, Nil)._3
  }

  def lookup[E](key: String): QueryM[E,Seq[String]] =
    QueryM {
      (params, _, log0) =>
        (Some(key), log0, params.getOrElse(key, Seq()))
    }


  /* Functions that are useful arguments to QueryM.is */
  def required(xs: Seq[String]): Option[String] =
    if(xs.length == 1) Some(xs(0))
    else None

  def forbidden(xs: Seq[String]): Option[Unit] =
    if(xs.length == 0) Some(())
    else None

  def optional(xs: Seq[String]): Option[Option[String]] =
    if(xs.length > 1) None  // trigger error
    else if(xs.length == 1) Some(Some(xs(0)))
    else Some(None) // no error, but no value either

  def int(opt: Option[String]): Option[Int] =
    try { opt.map(_.toInt) } catch { case _ => None }

  def pred[A](p: A => Boolean): Option[A] => Option[A] =
    opt => opt.flatMap(a => if(p(a)) opt else None)

  val even = pred((_:Int) % 2 == 0)
  val odd = pred((_:Int) % 2 == 1)
}
