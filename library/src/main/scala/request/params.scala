package unfiltered.request

import unfiltered.response.ResponseFunction
import scala.util.control.Exception.allCatch
import java.net.URLDecoder

/** Basic parameter acess, and a pattern matching extractor in Extract. */
object Params {
  type Map = scala.collection.Map[String, Seq[String]]
  /**
   * Given a req, extract the request params into a (Map[String, Seq[String]], request).
   * The Map is assigned a default value of Nil, so param("p") would return Nil if there
   * is no such parameter, or (as normal for servlets) a single empty string if the
   * parameter was supplied without a value. */
  def unapply[T](req: HttpRequest[T]) = {
    val names = req.parameterNames
    Some(((Map.empty[String, Seq[String]] /: names) ((m, n) =>
        m + (n -> req.parameterValues(n))
      )).withDefaultValue(Nil))
  }

  /**
   * A function Seq[String] => Option[B]  used to test and transform values
   * from the parameter map. Conditions may be chained with `~>` */
  class ParamMapper[B](f: Seq[String] => Option[B]) extends (Seq[String] => Option[B]) {
    def apply(a: Seq[String]) = f(a)
    def ~> [C](that: Option[B] => Option[C]) = new ParamMapper({ f andThen that })
  }

  /** Maps first parameter, if present. */
  val first = new ParamMapper(_.headOption)

  /**
   * Base class for parameter extractor objects, may be extended inline with
   * chained ParamMapper objects. */
  class Extract[E,T](f: Map => Option[T]) {
    def this(name: String, f: Seq[String] => Option[T]) =
      this({ params: Map => f(params(name)) })
    def unapply(params: Map) = f(params)
  }
  /** Construct a parameter predicate */
  def pred[E,A](p: A => Boolean): Option[A] => Option[A] =
    opt => opt filter p

  def int(os: Option[String]) =
    os.flatMap { s => allCatch.opt { s.toInt } }

  def long(os: Option[String]) =
    os.flatMap { s => allCatch.opt { s.toLong } }

  def float(os: Option[String]) =
    os.flatMap { s => allCatch.opt { s.toFloat } }

  def double(os: Option[String]) =
    os.flatMap { s => allCatch.opt { s.toDouble } }

  val even = pred { (_:Int) % 2 == 0 }
  val odd = pred { (_:Int) % 2 == 1 }

  def trimmed(s: Option[String]) = s map { _.trim }
  val nonempty = pred { !(_:String).isEmpty }
}

/** Basic query parameter acess. */
object QueryParams {
  
  /**
   * Given a req, extract the request query params into a (Map[String, Seq[String]], request).
   * The Map is assigned a default value of Nil, so param("p") would return Nil if there
   * is no such parameter, or (as normal for servlets) a single empty string if the
   * parameter was supplied without a value. */
  def unapply[T](req: HttpRequest[T]) = Some(urldecode(req.uri))
  
  def urldecode(enc: String) : Map[String, Seq[String]] = {
    def decode(raw: String) = URLDecoder.decode(raw, "UTF-8")
    val params = enc.dropWhile('?'!=).dropWhile('?'==)
    val pairs: Seq[(String,String)] = params.split('&').flatMap {
      _.split('=') match {
        case Array(key, value) => List((decode(key), decode(value)))
        case Array(key) if key != "" => List((decode(key), ""))
        case _ => Nil
      }
    }
    pairs.groupBy(_._1).map(t => (t._1, t._2.map(_._2))).toMap.withDefault { _ => Nil }
  }
  
}

/** Fined-grained error reporting for arbitrarily many failing parameters.
 * Import QParams._ to use; see ParamsSpec for examples. */
object QParams {
  type Log[E] = List[Fail[E]]
  type QueryFn[E,A] = (Params.Map, Option[String], Log[E]) =>
    (Option[String], Log[E], A)
  type QueryResult[E,A] = Either[Log[E], A]
  /** Left if the query has failed, right if it has not (but may be empty) */
  type Report[E,A] = Either[E,Option[A]]
  type Reporter[E,A,B] = Option[A] => Report[E,B]

  case class Fail[E](name: String, error: E)

  /* Implicitly provide 'orFail' for QueryResult (either) type. */
  case class QueryResultX[E,A](r: QueryResult[E,A]) {
    def orFail[B >: A](handler: Log[E] => B) =
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
    def is[B](f: A => Report[E,B]): QueryM[E,Option[B]] =
      QueryM {
        (params, key0, log0) =>
          val (key1, log1, value) = exec(params, key0, log0)
          key1 match {
            case None => (key1, log1, None) // do not record error
            case Some(k) =>
              f(value) match {
                case Left(err) => (None, Fail(k,err)::log1, None) // do record
                case Right(v) => (key1, log1, v)
              }
          }
      }

    def apply(params: Params.Map) = exec(params, None, Nil)._3
  }

  /** Create a validion token from a named value from the input Params.Map */
  def lookup[E](key: String): QueryM[E,Option[String]] =
    QueryM {
      (params, _, log0) =>
        (Some(key), log0, params.get(key).flatMap { _.headOption })
    }

  /** Create and name a validation token for an external input */
  def external[E, A](key: String, value: Option[A]): QueryM[E,Option[A]] =
    QueryM {
      (params, _, log0) =>
        (Some(key), log0, value)
    }

  /* Functions that are useful arguments to QueryM.is */
  def required[E,A](err:E): Reporter[E,A,A] = {
    case None => Left(err)
    case oa => Right(oa)
  }

  def optional[E,A](xs: Option[A]): Report[E,Option[A]] =
    Right(Some(xs))

  /** Promote c to an error reporter that fails if Some input is discarded */
  def watch[E,A,B](c: Option[A] => Option[B], err: A => E): Reporter[E,A,B] = {
    case None => Right(None)
    case Some(a) => c(Some(a)).map { b => Right(Some(b)) } getOrElse Left(err(a))
  }

  /** Convert a predicate into an error reporter */
  def pred[E,A](p: A => Boolean, err: A => E): Reporter[E,A,A] =
    watch({_ filter p}, err)

  /** Convert f into an error reporter that never reports errors */
  def ignore[E,A](f: Option[A] => Option[A]): Reporter[E,A,A] =
    opt => Right(f(opt))

  def int[E](e: String => E) = watch(Params.int, e)
  def long[E](e: String => E) = watch(Params.long, e)
  def even[E](e: Int => E) = watch(Params.even, e)
  def odd[E](e: Int => E) = watch(Params.odd, e)
  def trimmed[E] = ignore[E,String](Params.trimmed)
  def nonempty[E](e: E) = watch(Params.nonempty, (s: String) => e)
}
