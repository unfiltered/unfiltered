package unfiltered.response
import unfiltered.request._

trait ResponseFunction { def apply[T](res: HttpResponse[T]): HttpResponse[T] }

trait Responder extends ResponseFunction { self =>
  def apply[T](res: HttpResponse[T]) = {
    respond(res)
    res
  }
  def respond[T](res: HttpResponse[T])
  def ~> (that: ResponseFunction) = new Responder {
    def respond[T](res: HttpResponse[T]) {
      that(self(res))
    }
  }
}

class ChainResponse(f: ResponseFunction) extends Responder {
  def respond[T](res: HttpResponse[T]) { f(res) }
}

/** Tells the binding implentation to treat the request as non-matching */
object Pass extends ResponseFunction {
  def apply[T](res: HttpResponse[T]) = res
}
