package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

object AsyncCycle {
  /** A builder for promised cycling kits. */
  def map[M,A,B](mapping: M => ResponseFunction[B])(
    promised: PartialFunction[HttpRequest[A], {
      // don't use ResponsePromise _here_ because it impedes inferencer
      def foreach[U](f: M => U)
    }]
  ) = unfiltered.Async.Intent[A,B] {
    case req =>
      promised.lift(req) match {
        case Some(eachable) => eachable.foreach { (m: M) =>
          req.respond(mapping(m))
        }
        case None => Pass
      }
  }
  /**
   * Adapts a promise of Either[Throwable[ResponseFunction[B]]]
   * for an Async.Intent plan. If a Left Throwable is contained,
   * it's rethrown to be caught by the plan's exception handler.
   */
  def rethrow[A,B] = 
    AsyncCycle.map[Either[Throwable, ResponseFunction[B]],A,B] { either =>
      either.fold(
        e => throw e,
        identity
      )
    } _

  /**
   * Adapts a promise of Response[ResponseFunction[B]]
   * for an Async.Intent plan. This promise must be "perfect"
   * in that `foreach` will always be called with a response.
   * If it isn't called (e.g. because an exception is not handled)
   * the client will never receive a respnose.
   *
   * Don't use `perfect` unless you know what you're doing.
   */
  def perfect[A,B] = AsyncCycle.map[ResponseFunction[B],A,B](identity) _
}
