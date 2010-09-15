package unfiltered.plan

import unfiltered.request._
import unfiltered.response._

/** To ecapsulate a filter in a class definition */
trait Plan {
  def filter: PartialFunction[HttpRequest[_ <: AnyRef], ResponseFunction]
}

/** To define a filter class with an independent function */
class Planify(val filter: PartialFunction[HttpRequest[_ <: AnyRef], ResponseFunction]) extends Plan

/** To create a filter instance with an independent function */
object Planify {
  def apply(filter: PartialFunction[HttpRequest[_ <: AnyRef], ResponseFunction]) = new Planify(filter)
}
