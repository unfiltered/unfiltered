package unfiltered.response
import unfiltered.request._

object ResponsePackage {
  // make a package object when 2.7 support is dropped
  trait ResponseFunction { def apply[T](res: HttpResponse[T]): HttpResponse[T] }
}
import ResponsePackage.ResponseFunction

/** Pass on to the next servlet filter */
object Pass extends ResponseFunction {
  def apply[T](res: HttpResponse[T]) = res
}

/** Pass on the the next filter then execute `later` after */
case class PassAndThen(later: PartialFunction[HttpRequest, ResponseFunction]) extends ResponseFunction  {
  def apply[T](res: HttpResponse[T]) = res
  def then[T](req: HttpRequest) = later.orElse[HttpRequest, ResponseFunction] { case _ => Pass } (req)
}

/** Companion of PassAndThen(later). Return this in plans to execute a fn later */
object PassAndThen {
  def after(later: PartialFunction[HttpRequest, ResponseFunction]) = PassAndThen(later)
}

trait Responder extends ResponseFunction {
  def apply[T](res: HttpResponse[T]) = {
    respond(res)
    res
  }
  def respond[T](res: HttpResponse[T])
  def ~> (that: ResponseFunction) = new ResponseFunction {
    def apply[T](res: HttpResponse[T]) = that(Responder.this(res))
  }
}

class ChainResponse(f: ResponseFunction) extends Responder {
  def respond[T](res: HttpResponse[T]) = f(res)
}
