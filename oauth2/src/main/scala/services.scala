package unfiltered.oauth2

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

/** A ResourceOwner belongs to a Service */
trait ResourceOwner {
  def id: String
  def password: Option[String]
}

/** Encapsulates information sent by a Client Authorization request that may
 *  need to be repeated after authentication, account creation, or other container
 *  behavior before an authorization request can be processed */
case class RequestBundle[T](request: Req[T], responseTypes: Seq[String], client: Client,
                            owner: Option[ResourceOwner], redirectUri: String,
                            scope: Seq[String], state: Option[String])

/** Request responses a Service must implement to complete OAuth flows */
trait ServiceResponses {

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



  /** @return a function that provides a user notification that a request was made with an invalid client */
  def invalidClient: ResponseFunction[Any]
}

trait Service extends ServiceResponses {

  /** @return a uri for more information on a privded error code */
  def errorUri(error: String): Option[String]

  /** @return Some(resourceOwner) if one is authenticated, None otherwise. None will trigger a login request */
  def resourceOwner[T](r: Req[T]): Option[ResourceOwner]

  /** @return Some(resourceOwner) if they can be authenticated by the given password credentials, otherwise None */
  def resourceOwner(userName: String, password: String): Option[ResourceOwner]

  /** @return true if application-specific logic determines this request was accepted, false otherwise */
  def accepted[T](r: Req[T]): Boolean

  /** @return true if application-specific logic determines this request was denied, false otherwise */
  def denied[T](r: Req[T]): Boolean

  /** @return true if provides scopes are valid and not malformed */
  def validScopes(scopes: Seq[String]): Boolean

  /** @return true if the provided scopes are valid for a given client and resource owner */
  def validScopes[T](resourceOwner: ResourceOwner, scopes: Seq[String], req: Req[T]): Boolean
}
