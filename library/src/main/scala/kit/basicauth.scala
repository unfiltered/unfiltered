package unfiltered.kit

import unfiltered.request.BasicAuth
import unfiltered.response._

/** Self-contained basic auth */
object Auth {
  val DefaultFail = Unauthorized ~> WWWAuthenticate("Digest")
  def basic[A,B](is: (String, String) => Boolean)(
    intent: unfiltered.Cycle.Intent[A,B], onFail: ResponseFunction[B] = DefaultFail) = {
    intent.fold(
      { _ => Pass },
      {
        case (BasicAuth(u, p), rf) => if(is(u,p)) rf else onFail
        case _ => onFail
      }
    )
  }
}
