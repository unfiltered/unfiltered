package unfiltered.request

import unfiltered.Cookie

object Cookies {
  def unapply[T](r: HttpRequest[T]) =
    Some((((Map.empty[String, Option[Cookie]] /: r.cookies)(
      (m,c) => m + (c.name -> Some(c)))).withDefaultValue(None), r))
      
  def apply[T](r: HttpRequest[T]): Map[String, Option[Cookie]] = Cookies.unapply(r).get._1
}