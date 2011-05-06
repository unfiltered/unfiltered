package unfiltered.oauth2

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

/** A ResourceOwner belongs to a Container */
trait ResourceOwner {
  def id: String
}

/** Encapsulates information sent by a Client Authorization request that may
 *  need to be repeated after authentication, account creation, or other container
 *  behavior before an authorization request can be processed */
case class RequestBundle[T](request: Req[T], responseType: String, client: Client,
                            owner: Option[ResourceOwner], scope: Option[String],
                            state: Option[String])

/** Request responses a Container must implement to complete OAuth flows */
trait ContainerResponses {

  /** @return a function that provides a means of logging a user in.
   *          the handling of a successfully login should post back to
   *          the authorization server's authorize endpoint */
  def login[T](requestBundle: RequestBundle[T]): ResponseFunction[Any]

  /** @return a function that provides a means of promting a resource ower
   *          for authorization. The handling of this response should post
   *          back to the authorization server's authorize endpoitn */
  def requestAuthorization[T](requestBundle: RequestBundle[T]): ResponseFunction[Any]

  /** @return a function that provides a user notification that a provided redirect
   *          uri was invalid or not present */
  def invalidRedirectUri(uri: Option[String], client: Option[Client]): ResponseFunction[Any]
}

trait Container extends ContainerResponses {

  /** @return Some(resourceOwner) if one is authenticated, None otherwise. None will trigger a login request */
  def resourceOwner: Option[ResourceOwner]

    /** @return true if application-specific logic determines this request was accepted, false otherwise */
  def accepted[T](r: Req[T]): Boolean

  /** @return true if application-specific logic determines this request was denied, false otherwise */
  def denied[T](r: Req[T]): Boolean

  /** @return true if the provided scopes are valid for a given client and resource owner */
  def validScopes[T](resourceOwner: ResourceOwner, scopes: Option[String], req: Req[T]): Boolean
}
