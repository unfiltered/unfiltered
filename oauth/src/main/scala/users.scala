package unfiltered.oauth

import unfiltered.response._
import unfiltered.request.{HttpRequest => Req}

/** minimal user identity */
trait UserLike {
  val id: String
}

trait UserHost extends OAuthTemplates {

  /** @return Some(user) if user is logged in, None otherwise */
  def current[T](r: Req[T]): Option[UserLike]
  
  /** @return true if app logic determines this request was accepted, false otherwise */
  def accepted[T](token: String, r: Req[T]): Boolean

  /** @return true if app logic determines this request was denied, false otherwise */
  def denied[T](token: String, r: Req[T]): Boolean
  
  /** @return the html to display to the user to log in */
  def login(token: String): Html 

  /** @return the html to show a user to provide a consumer with a verifier */
  def oobResponse(verifier: String): Html
  
  /** @return http response for confirming the user's denial was processed */
  def deniedConfirmation(consumer: Consumer): Html = layout(
    <div>You have denied a 3rd party access to your data</div>
  )
  
  /** @todo more flexibilty wrt exensibility */
  def requestAcceptance(token: String, consumer: Consumer): Html = layout(
    <div>
      <p>
        A 3rd party application has requested access to your data.
      </p>
      <form action="/oauth/authorize" method="POST">
        <input type="hidden" name="oauth_token" value={token} />
        <input type="submit" name="submit" value="Approve"/>
        <input type="submit" name="submit" value="Deny"/>
      </form>
    </div>)
}
