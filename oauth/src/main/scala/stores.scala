package unfiltered.oauth

/** OAuth storage and host dependencies */
trait OAuthStores {
  /** access to nonces */
  val nonces: NonceStore
  /** access to tokens */
  val tokens: TokenStore
  /** access to consumers */
  val consumers: ConsumerStore
  /** access to users and host interactions */
  val users: UserHost
}
