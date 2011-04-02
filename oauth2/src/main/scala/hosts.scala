package unfiltered.oauth2

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

trait HostResponses {
  /** @return a function that provides a means of logging a user in */
  def login(token: String): ResponseFunction[Any]
  /** @return a function that provides a user with a means of confirming the user's denial was processed */
  def deniedConfirmation(consumer: Client): ResponseFunction[Any]
  /** @return a function that provides a user with a means of asking for accepting a consumer's
   *    request for access to their private resources */
  def requestAcceptance(token: String, responseType: String, consumer: Client, scope: Option[String]): ResponseFunction[Any]
  /** @return a function that provides a user notification that a provided redirect uri was invalid */
  def invalidRedirectUri(uri: String, client: Client): ResponseFunction[Any]
}

trait Host extends HostResponses {
  def currentUser: Option[ResourceOwner]
    /** @return true if app logic determines this request was accepted, false otherwise */
  def accepted[T](r: Req[T]): Boolean
  /** @return true if app logic determines this request was denied, false otherwise */
  def denied[T](r: Req[T]): Boolean
  def validScopes[T](r: Req[T], scopes: Option[String]): Boolean
}
