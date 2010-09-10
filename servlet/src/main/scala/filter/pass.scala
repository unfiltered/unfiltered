package unfiltered.servlet.filter

import unfiltered.servlet._
import unfiltered.request._
import unfiltered.response._

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply[T](res: HttpResponse[T]) = res
}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen(later: PartialFunction[ServletRequestWrapper, ResponseFunction]) extends ResponseFunction  {
  def apply[T](res: HttpResponse[T]) = res
  def then(req: ServletRequestWrapper) = later.orElse[ServletRequestWrapper, ResponseFunction] { case _ => Pass } (req)
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after[T](later: PartialFunction[ServletRequestWrapper, ResponseFunction]) = PassAndThen(later)
}
