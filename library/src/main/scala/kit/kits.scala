package unfiltered.kit

import unfiltered.request._
import unfiltered.response._
import unfiltered.{Cycle,Async}

/** A kit that conditionally prepends a response function */
trait Prepend { self =>
  def intent: Cycle.Intent[Any,Any]
  /** The produced intent is defined for all inputs, is Pass
   *  where the given intent parameter is not defined. */
  def apply[A,B](intent: unfiltered.Cycle.Intent[A,B]) =
    Cycle.Intent[A,B] {
      case req =>
        Pass.fold(
          intent,
          (_: HttpRequest[A]) => Pass,
          (_: HttpRequest[A], rf: ResponseFunction[B]) =>
            Pass.orElse(
              self.intent,
              (_: HttpRequest[A]) => NoOpResponder
            )(req) ~> rf
        )(req)
    }

  def async[A,B](intent: Async.Intent[A,B]) =
    Async.Intent[A,B] {
      case req =>
        val dreq = new DelegatingRequest(req) with Async.Responder[B] {
          def respond(rf: unfiltered.response.ResponseFunction[B]) {
            val kitRf = self.intent.lift(req).getOrElse(NoOpResponder)
            req.respond(kitRf ~> rf)
          }
        }
        Pass.orElse(
          intent,
          (_: HttpRequest[A]) => Pass
        )(dreq)
    }
}

object NoOpResponder extends Responder[Any] {
  def respond(res: HttpResponse[Any]) { }
}
