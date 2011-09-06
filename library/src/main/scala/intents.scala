package unfiltered

import unfiltered.request.HttpRequest
import unfiltered.response.{ResponseFunction,HttpResponse,Pass}

object Cycle {
  /** A rountrip intent is a set of instructions for producting
   * a complete response to a request. Plans that contain intents
   * of this type can be run against a general set of tests. */
  type Intent[-A,-B] = PartialFunction[HttpRequest[A], ResponseFunction[B]]
  /** Object to facilitate Cycle.Intent definitions. Type annotations
   *  are another option. */
  object Intent {
    def apply[A,B](intent: Intent[A,B]) = intent
    def complete[A,B](intent: Intent[A,B]): Intent[A,B] =
      intent.orElse({ case _ => Pass })
  }
}
