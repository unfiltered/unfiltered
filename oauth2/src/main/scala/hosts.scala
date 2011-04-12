package unfiltered.oauth2

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}


trait ResourceOwner {
  def id: String
}

trait HostResponses {
  /** @return a function that provides a means of logging a user in.
   *          the handling of a successfully login should post back to
   *          the authorization server's authorize endpoint */
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
  /** @return Some(resourceOwner) if there is one authenticated, None otherwise */
  def resourceOwner: Option[ResourceOwner]

    /** @return true if application-specific logic determines this request was accepted, false otherwise */
  def accepted[T](r: Req[T]): Boolean

  /** @return true if application-specifi logic determines this request was denied, false otherwise */
  def denied[T](r: Req[T]): Boolean

  /** @return true if the provided scopes are valid for a given client and resource owner */
  def validScopes[T](resourceOwner: ResourceOwner, scopes: Option[String], req: Req[T]): Boolean
}
