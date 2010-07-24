package unfiltered.response

import javax.servlet.http.{HttpServletRequest,HttpServletResponse}

object ResponsePackage {
  // make a package object when 2.7 support is dropped
  type ResponseFunction = HttpServletResponse => HttpServletResponse
}
import ResponsePackage.ResponseFunction

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply(res: HttpServletResponse) = res
}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen[T](later: PartialFunction[(HttpServletRequest, HttpServletResponse), T]) extends ResponseFunction  {
  def apply(res: HttpServletResponse) = res
  def then[T](req: HttpServletRequest, resp: HttpServletResponse) = later((req, resp)) 
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after[T](later: PartialFunction[(HttpServletRequest, HttpServletResponse), T]) = PassAndThen(later)
}

trait Responder extends ResponseFunction {
  def apply(res: HttpServletResponse) = { 
    respond(res)
    res
  }
  def respond(res: HttpServletResponse)
  def ~> (that: ResponseFunction) = new ChainResponse(this andThen that)
}
class ChainResponse(f: ResponseFunction) extends Responder {
  def respond(res: HttpServletResponse) = f(res)
}