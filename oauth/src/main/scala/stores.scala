package unfiltered.oauth

import unfiltered.request.{HttpRequest => Req}

trait OAuthStores {
  val nonces: NonceStore
  val tokens: TokenStore
  val consumers: ConsumerStore
  val users: UserHost
}

trait MockOAuthStores extends OAuthStores {
  val nonces = new NonceStore {
    def put(consumer: String, timestamp: String, nonce: String) =
     ("ok", 201)
  }
  
  val tokens: TokenStore = new DefaultTokenStore {
    val map = Map("key" -> RequestToken("key", "sec", "ckey", "cb"))
    def put(token: Token) = token // mock
    def get(tokenId: String) = map.get(tokenId)
    def delete(tokenId: String) = ()
  }
  
  val consumers = new ConsumerStore {
    def get(consumerKey: String) = Some(new Consumer{
      val key = "key"
      val secret = "secret"
    })
  }

  val users = new UserHost {
    def current[T](r: Req[T]) = Some(new UserLike { val id = "test_user" })
    
    def accepted[T](token: String, r: Req[T]) = true

    def login(token: String) = unfiltered.response.Html(<form action="#"><input type="hidden" name="token" value={token}/><label for="n">username</label> <input type="text" name="n"></input> <label form="p">password</label><input type="password" name="p"></input></form>)
  }
}
