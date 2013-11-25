package unfiltered.kit

import unfiltered.Async
import unfiltered.request.HttpRequest
import unfiltered.response.{ResponseFunction,Pass,InternalServerError}

import scala.concurrent.{Future,ExecutionContext}
import scala.util.{Failure, Success}

/** Converts an intent for a future of response fuction into an async responder */
object Futured {
  type Intent[A,B] = PartialFunction[HttpRequest[A], Future[ResponseFunction[B]]]
  def apply[A,B](intent: Intent[A,B])
                (implicit executionContextIn: ExecutionContext): Async.Intent[A,B] =
    Pass.fold(
      intent,
      _ => Pass,
      (req, rff: Future[ResponseFunction[B]]) =>
      rff.onComplete {
        case Success(r) => req.respond(r)
        case Failure(t) => InternalServerError
      }
    )
}
