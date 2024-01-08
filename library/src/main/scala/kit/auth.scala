package unfiltered.kit

import unfiltered.request.BasicAuth
import unfiltered.response._
import unfiltered.request.HttpRequest

/** Self-contained basic auth */
object Auth {
  def defaultFail(realm: String): ResponseFunction[Any] =
    Unauthorized ~> WWWAuthenticate("""Basic realm="%s"""" format realm)
  def basic[A, B](
    is: (String, String) => Boolean,
    realm: String = "secret"
  )(
    intent: unfiltered.Cycle.Intent[A, B],
    onFail: ResponseFunction[B] = defaultFail(realm)
  ): PartialFunction[HttpRequest[A], ResponseFunction[B]] = {
    intent.fold(
      { _ => Pass },
      {
        case (BasicAuth(u, p), rf) => if (is(u, p)) rf else onFail
        case _ => onFail
      }
    )
  }
}
