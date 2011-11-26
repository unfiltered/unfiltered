package unfiltered.kit

import unfiltered.request._
import unfiltered.response._
import unfiltered.{Cycle,Async}

/** A kit that conditionally prepends a response function */
trait Prepend { self =>
  def intent: Cycle.Intent[Any,Any]
  private def intentOrNoOp = Pass.onPass(
    intent,
    (_: HttpRequest[_]) => NoOpResponder
  )
  /** The produced intent is defined for all inputs, is Pass
   *  where the given intent parameter is not defined. */
  def apply[A,B](intent: unfiltered.Cycle.Intent[A,B]) =
    Pass.fold(
      intent,
      (_: HttpRequest[A]) => Pass,
      (req: HttpRequest[A], rf: ResponseFunction[B]) =>
        (intentOrNoOp)(req) ~> rf
    ): Cycle.Intent[A,B]

  def async[A,B](intent: Async.Intent[A,B]) =
    Async.Intent[A,B] {
      case req =>
        val dreq = new DelegatingRequest(req) with Async.Responder[B] {
          def respond(rf: unfiltered.response.ResponseFunction[B]) {
            req.respond(intentOrNoOp(req) ~> rf)
          }
        }
        Pass.onPass(
          intent,
          (_: HttpRequest[A]) => Pass
        )(dreq)
    }
}

object NoOpResponder extends Responder[Any] {
  def respond(res: HttpResponse[Any]) { }
}
