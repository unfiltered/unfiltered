package unfiltered.request

import javax.servlet.http.HttpServletRequest

object Params {
  /** Dress a Java Enumeration in Scala Iterator clothing */
  case class JEnumerationIterator[T](e: java.util.Enumeration[T]) extends Iterator[T] {
    def hasNext: Boolean =  e.hasMoreElements()
    def next: T = e.nextElement()
  }
  type ParamMap = Map[String, Seq[String]]
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

  abstract class Named[T](name: String, f: Seq[String] => Option[T]) {
    def unapply(params: ParamMap) = f(params(name)) map {
      (_, params)
    }
  }

  class Chained[T, R](f: T => R) extends (T => R) {
    def apply(t: T) = f(t)
    def ~> [Then] (that: R => Then) = new Chained(this andThen that)
  }

  val first = new Chained( { seq: Seq[String] => seq.headOption } )

  val trimmed = { (_: Option[String]) map { _.trim } }
  val nonempty = { (_: Option[String]) filter { ! _.isEmpty  } }

  val int = { s: Option[String] =>
    try { s map { _.toInt } } catch { case _ => None }
  }
}