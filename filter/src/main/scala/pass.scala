package unfiltered.filter

import unfiltered.request._
import unfiltered.response._
import javax.servlet.http.{HttpServletRequest,HttpServletResponse}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen(later: PartialFunction[HttpRequest[HttpServletRequest], ResponseFunction]) extends ResponseFunction  {
  def apply[T](res: HttpResponse[T]) = res
  def then(req: HttpRequest[HttpServletRequest]) = later.orElse[HttpRequest[HttpServletRequest], ResponseFunction] { case _ => Pass } (req)
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after[T](later: PartialFunction[HttpRequest[HttpServletRequest], ResponseFunction]) = PassAndThen(later)
}
