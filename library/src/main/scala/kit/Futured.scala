package unfiltered.kit

import unfiltered.Async
import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction
import unfiltered.response.Pass
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Failure
import scala.util.Success

/** Converts an intent for a future of response function into an async responder */
object Futured {
  type Intent[-A, -B] = PartialFunction[HttpRequest[A], Future[ResponseFunction[B]]]
  def apply[A, B](intent: Intent[A, B])(
    onException: (HttpRequest[A], Throwable) => Unit
  )(implicit executionContextIn: ExecutionContext): Async.Intent[A, B] =
    Pass.fold(
      intent,
      _ => Pass,
      (req, rff: Future[ResponseFunction[B]]) =>
        rff.onComplete {
          case Success(r) => req.respond(r)
          case Failure(t) => onException(req, t)
        }
    )
}
