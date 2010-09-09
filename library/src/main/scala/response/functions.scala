package unfiltered.response
import unfiltered.request._

object ResponsePackage {
  // make a package object when 2.7 support is dropped
  type ResponseFunction = HttpResponse => HttpResponse
}
import ResponsePackage.ResponseFunction

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply(res: HttpResponse) = res
}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen(later: PartialFunction[HttpServletRequest, ResponseFunction]) extends ResponseFunction  {
  def apply(res: HttpResponse) = res
  def then(req: HttpServletRequest) = later.orElse[HttpServletRequest, ResponseFunction] { case _ => Pass } (req)
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after(later: PartialFunction[HttpServletRequest, ResponseFunction]) = PassAndThen(later)
}

trait Responder extends ResponseFunction {
  def apply(res: HttpResponse) = {
    respond(res)
    res
  }
  def respond(res: HttpResponse)
  def ~> (that: ResponseFunction) = new ChainResponse(this andThen that)
}
class ChainResponse(f: ResponseFunction) extends Responder {
  def respond(res: HttpResponse) = f(res)
}