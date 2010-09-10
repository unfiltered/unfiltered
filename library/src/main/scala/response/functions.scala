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
