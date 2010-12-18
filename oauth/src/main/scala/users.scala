package unfiltered.oauth

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

/** minimal user identity */
trait UserLike {
  val id: String
}

trait HostResponses {
  /** @return the html to display to the user to log in */
  def login[T](token: String): Responder[T]

  /** @return the html to show a user to provide a consumer with a verifier */
  def oobResponse[T](verifier: String): Responder[T]

  /** @return http response for confirming the user's denial was processed */
  def deniedConfirmation[T](consumer: Consumer): Responder[T]

  /** @todo more flexibilty wrt exensibility */
  def requestAcceptance[T](token: String, consumer: Consumer): Responder[T]
}

trait UserHost extends HostResponses {

  /** @return Some(user) if user is logged in, None otherwise */
  def current[T](r: Req[T]): Option[UserLike]

  /** @return true if app logic determines this request was accepted, false otherwise */
  def accepted[T](token: String, r: Req[T]): Boolean

  /** @return true if app logic determines this request was denied, false otherwise */
  def denied[T](token: String, r: Req[T]): Boolean
}
