package unfiltered.oauth

/** Mock implementatin of OAuthStores for testing */
trait MockOAuthStores extends OAuthStores {
  import unfiltered.request.{HttpRequest => Req}

  /** access to nonces */
  val nonces = new NonceStore {
    /** all nonces are considered unique */
    def put(consumer: String, timestamp: String, nonce: String) = true
  }

  /** access to tokens */
  val tokens: TokenStore = new DefaultTokenStore {
    val map = Map("key" -> RequestToken("key", "sec", "ckey", "cb"))
    def put(token: Token) = token // mock
    def get(tokenId: String) = map.get(tokenId)
    def delete(tokenId: String) = ()
  }

  /** access to consumers */
  val consumers = new ConsumerStore {
    def get(consumerKey: String) = Some(new Consumer{
      val key = "key"
      val secret = "secret"
    })
  }

  /** access to user host */
  val users = new UserHost {
    import unfiltered.response.Html

    def current[T](r: Req[T]) = Some(new UserLike { val id = "test_user" })

    def accepted[T](token: String, r: Req[T]) = true

    def denied[T](token: String, r: Req[T]) = false

    def login(token: String) =
      Html(<form action="#">
         <input type="hidden" name="token" value={token}/>
         <label for="n">username</label>
         <input type="text" name="n"></input>
         <label form="p">password</label>
         <input type="password" name="p"></input>
         </form>)

    def oobResponse(verifier: String) =
      Html(<p id="verifier">{verifier}</p>)

    def requestAcceptance(token: String, consumer: Consumer) =
      Html(<p>Access to your data was denied</p>)

    def deniedConfirmation(consumer: Consumer) =
      Html(<p>a third party is requesting access to your data</p>)
  }
}
