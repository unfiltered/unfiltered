package unfiltered.oauth2

import unfiltered.request.HttpRequest
import unfiltered.response.ResponseFunction

/** Defines a composition of oauth flows */
trait Flows extends AuthCodeFlow
  with TokenFlow with PasswordFlow with ClientCredentialsFlow with Refreshing

trait AuthCodeFlow {
  def onAuthCode[T](
    req: HttpRequest[T],
    responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]): ResponseFunction[Any]

  def onGrantAuthCode(
    code: String, redirectUri: String,
    clientId: String, clientSecret: String): ResponseFunction[Any]
}

trait TokenFlow {
  def onToken[T](
    req: HttpRequest[T], responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]): ResponseFunction[Any]
}

trait PasswordFlow {
  def onPassword(
    userName: String, password: String,
    clientId: String, clientSecret: String,
    scope: Seq[String]): ResponseFunction[Any]
}

trait ClientCredentialsFlow {
  def onClientCredentials(
    clientId: String, clientSecret: String,
    scope: Seq[String]): ResponseFunction[Any]
}

trait Refreshing {
  def onRefresh(
    refreshToken: String, clientId: String,
    clientSecret: String, scope: Seq[String]): ResponseFunction[Any]
}
