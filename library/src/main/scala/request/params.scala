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
  def pred[E,A](p: A => Boolean): Option[A] => Option[A] =
    opt => opt filter p

  def int(os: Option[String]) = 
    try { os map { _.toInt } } catch { case _ => None }

  val even = pred((_:Int) % 2 == 0)
  val odd = pred((_:Int) % 2 == 1)
}

object QParams {
  type Log[E] = List[(String,E)]
  type QueryFn[E,A] = (Params.Map, Option[String], Log[E]) =>
    (Option[String], Log[E], A)
  type QueryResult[E,A] = Either[Log[E], A]
  /** Left if the query has failed, right if it has not (but may be empty) */
  type ParamState[E,A] = Either[E,Option[A]]

  /* Implicitly provide 'orElse' for QueryResult (either) type. */
  case class QueryResultX[E,A](r: QueryResult[E,A]) {
    def orElse[B >: A](handler: Log[E] => B) =
      r.left.map(handler) match { // i.e. .merge, in 2.8
        case Left(v) => v
        case Right(v) => v
      }
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
    def is[B](f: A => ParamState[E,B]): QueryM[E,Option[B]] =
      QueryM {
        (params, key0, log0) =>
          val (key1, log1, value) = exec(params, key0, log0)
          key1 match {
            case None => (key1, log1, None) // do not record error
            case Some(k) =>
              f(value) match {
                case Left(err) => (None, (k,err)::log1, None) // do record
                case Right(v) => (key1, log1, v)
              }
          }
      }

    def apply(params: Params.Map) = exec(params, None, Nil)._3
  }

  def all[E](key: String): QueryM[E,Seq[String]] =
    QueryM {
      (params, _, log0) =>
        (Some(key), log0, params.getOrElse(key, Seq()))
    }

  def first[E](key: String): QueryM[E,Option[String]] =
    QueryM {
      (params, _, log0) =>
        (Some(key), log0, params.get(key).flatMap { _.firstOption })
    }

  /* Functions that are useful arguments to QueryM.is */
  def required[E,A](err:E)(xs: Option[A]): ParamState[E,A] = 
    xs match {
      case None => Left(err)
      case oa => Right(oa)
    }

  def optional[E,A](xs: Option[A]): ParamState[E,Option[A]] =
    Right(Some(xs))

  /** Promote to a ParamState that fails if Some input is discarded */
  def watch[E,A,B](c: Option[A] => Option[B], err: E): Option[A] => ParamState[E,B] = {
    case None => Right(None)
    case oa => c(oa).map { b => Right(Some(b)) } getOrElse Left(err)
  }

  def pred[E,A](p: A => Boolean)(err: E): Option[A] => ParamState[E,A] =
    watch({_ filter p}, err)

  def int[E](e: E) = watch(Params.int, e)
  def even[E](e: E) = watch(Params.even, e)
  def odd[E](e: E) = watch(Params.odd, e)
}
