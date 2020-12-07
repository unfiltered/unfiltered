package unfiltered.request

import scala.util.control.Exception.allCatch
import java.net.URLDecoder

/** Basic parameter access, and a pattern matching extractor in Extract. */
object Params {
  type Map = scala.collection.Map[String, Seq[String]]
  /**
   * Given a req, extract the request params into a (Map[String, Seq[String]], request).
   * The Map is assigned a default value of Nil, so param("p") would return Nil if there
   * is no such parameter, or (as normal for servlets) a single empty string if the
   * parameter was supplied without a value. */
  def unapply[T](req: HttpRequest[T]): Some[Map] = {
    val names = req.parameterNames
    Some(names.foldLeft(Map.empty[String, Seq[String]]) ((m, n) =>
        m + (n -> req.parameterValues(n))
      ).withDefaultValue(Nil))
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
  class Extract[T](f: Map => Option[T]) {
    def this(name: String, f: Seq[String] => Option[T]) =
      this({ (params: Map) => f(params(name)) })
    def unapply(params: Map) = f(params)
  }
  /** Construct a parameter predicate */
  def pred[A](p: A => Boolean): Option[A] => Option[A] =
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

/** Basic query parameter access. */
object QueryParams {
  
  /**
   * Given a req, extract the request query params into a (Map[String, Seq[String]], request).
   * The Map is assigned a default value of Nil, so param("p") would return Nil if there
   * is no such parameter, or (as normal for servlets) a single empty string if the
   * parameter was supplied without a value. */
  def unapply[T](req: HttpRequest[T]): Some[Map[String, Seq[String]]] = Some(urldecode(req.uri))
  
  def urldecode(enc: String) : Map[String, Seq[String]] = {
    def decode(raw: String) = URLDecoder.decode(raw, "UTF-8")
    val params = enc.dropWhile('?'!= _).dropWhile('?'== _)
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
