package unfiltered.oauth

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

/** Respresents the `User` in an oauth interaction */
trait UserLike {
  val id: String
}

/** Provides a means of host-specific hooks into providing user interfaces */
trait HostResponses {
  /** @return a function that provides a means of logging a user in */
  def login(token: String): ResponseFunction[Any]

  /** @return a function that provides a consumer with a means of accessing verifier */
  def oobResponse(verifier: String): ResponseFunction[Any]

  /** @return a function that provides a user with a means of confirming the user's denial was processed */
  def deniedConfirmation(consumer: Consumer): ResponseFunction[Any]

  /** @return a function that provides a user with a means of asking for accepting a consumer's
   *    request for access to their private resources */
  def requestAcceptance(token: String, consumer: Consumer): ResponseFunction[Any]
}

trait UserHost extends HostResponses {

  /** @return Some(user) if user is logged in, None otherwise */
  def current[T](r: Req[T]): Option[UserLike]

  /** @return true if app logic determines this request was accepted, false otherwise */
  def accepted[T](token: String, r: Req[T]): Boolean

  /** @return true if app logic determines this request was denied, false otherwise */
  def denied[T](token: String, r: Req[T]): Boolean
}
