package unfiltered.oauth

import unfiltered.request.{HttpRequest => Req}

/** OAuth storage and host dependencies */
trait OAuthStores {
  /** access to nonces */
  val nonces: NonceStore
  /** acces to tokens */
  val tokens: TokenStore
  /** acces to cosumers */
  val consumers: ConsumerStore
  /** access to users and host interactions */
  val users: UserHost
}
