package unfiltered.plan

import unfiltered.request._
import unfiltered.response._

/** To ecapsulate a filter in a class definition */
trait Plan[T] {
  def filter: PartialFunction[HttpRequest[T], ResponseFunction]
}

/** To define a filter class with an independent function */
class Planify[T](val filter: PartialFunction[HttpRequest[T], ResponseFunction]) extends Plan[T]

/** To create a filter instance with an independent function */
object Planify {
  def apply[T](filter: PartialFunction[HttpRequest[T], ResponseFunction]) = new Planify(filter)
}
