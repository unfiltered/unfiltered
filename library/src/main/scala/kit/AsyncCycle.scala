package unfiltered.kit

import unfiltered.request._
import unfiltered.response._

object AsyncCycle {
  /** Structural of some promise, for when the inferencer needs help */
  type ResponsePromise[T] = {
    def foreach[U](f: ResponseFunction[T] => U)
  }

  /** Adapt an intent that promises a response function for
   *  an open-ended async handler */
  def apply[A,B](
    promised: PartialFunction[HttpRequest[A], {
      // don't use ResponsePromise _here_ because it impedes inferencer
      def foreach[U](f: ResponseFunction[B] => U)
    }]
  ) = unfiltered.Async.Intent[A,B] {
    case req =>
      promised.lift(req).getOrElse {
        Some(Pass): ResponsePromise[B]
      }.foreach(req.respond)
  }
}
