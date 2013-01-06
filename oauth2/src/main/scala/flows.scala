package unfiltered.oauth2

import unfiltered.request.HttpRequest
import unfiltered.response.{ BadRequest, ResponseFunction }

/** Defines a composition of oauth flows. Services may opt out of flows mixing in
 *  NoAuthCodes, NoTokens, NoPasswords, NoClientCredentials, or NoRefreshing */
trait Flows extends AuthCodeFlow
  with TokenFlow
  with PasswordFlow
  with ClientCredentialsFlow
  with Refreshing

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

trait NoAuthCodes extends AuthCodeFlow { self: AuthCodeFlow =>
  override def onAuthCode[T](req: HttpRequest[T],
    responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]) = BadRequest
  override def onGrantAuthCode(code: String, redirectUri: String,
    clientId: String, clientSecret: String) = BadRequest                                 
}

trait TokenFlow {
  def onToken[T](
    req: HttpRequest[T], responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]): ResponseFunction[Any]
}

trait NoTokens extends TokenFlow { self: TokenFlow => 
  def onToken[T](
    req: HttpRequest[T], responseType: Seq[String],
    clientId: String,
    redirectUri: String, scope: Seq[String],
    state: Option[String]) = BadRequest 
}

trait PasswordFlow {
  def onPassword(
    userName: String, password: String,
    clientId: String, clientSecret: String,
    scope: Seq[String]): ResponseFunction[Any]
}

trait NoPasswords extends PasswordFlow { self: PasswordFlow  =>
  def onPassword(
    userName: String, password: String,
    clientId: String, clientSecret: String,
    scope: Seq[String]) = BadRequest
}

trait ClientCredentialsFlow {
  def onClientCredentials(
    clientId: String, clientSecret: String,
    scope: Seq[String]): ResponseFunction[Any]
}

trait NoClientCredentials extends ClientCredentialsFlow { self: ClientCredentialsFlow =>
  def onClientCredentials(
    clientId: String, clientSecret: String,
    scope: Seq[String]) = BadRequest
}


trait Refreshing {
  def onRefresh(
    refreshToken: String, clientId: String,
    clientSecret: String, scope: Seq[String]): ResponseFunction[Any]
}

trait NoRefreshing extends Refreshing { self: Refreshing =>
  def onRefresh(
    refreshToken: String, clientId: String,
    clientSecret: String, scope: Seq[String]) = BadRequest
}

