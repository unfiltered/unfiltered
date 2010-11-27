package unfiltered.oauth

import org.specs._

object OAuthSpec extends Specification with unfiltered.spec.jetty.Served {
  
  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import dispatch._
  import dispatch.oauth._
  import dispatch.oauth.OAuth._  

  System.setProperty("file.encoding", "UTF-8")
  val consumer = dispatch.oauth.Consumer("key", "secret")
  
  def setup = { 
    _.filter(unfiltered.oauth.OAuth(new MockOAuthStores {
      var tokenMap = scala.collection.mutable.Map.empty[String, unfiltered.oauth.Token]

      override val consumers = new ConsumerStore {
        def get(key: String) = Some(new unfiltered.oauth.Consumer { 
          val key = consumer.key
          val secret = consumer.secret 
        })
      }
      
      override val tokens: TokenStore = new DefaultTokenStore {
        override def get(key: String) = tokenMap.get(key)
        override def put(token: unfiltered.oauth.Token) = {
          tokenMap += (token.key -> token)
          token
        }
        override def delete(key: String) = tokenMap -= key
      }
    }))
  }
  
  "oauth" should {
    "authenticate a valid consumers request using a HMAC-SHA1 signature with oob workflow" in {
      val h = new Http
      val payload = Map("identité" -> "caché", "identity" -> "hidden", "アイデンティティー" -> "秘密", 
        "pita" -> "-._~*")
      println("OAuthSpec consumer -> %s" format consumer)
      val request_token = h(host.POST / "request_token" << OAuth.callback(OAuth.oob) ++ payload <@ consumer as_token)
      println("OAuthSpec request_token -> %s" format request_token)
      val verifier = h(host / "authorize" <<? request_token as_str)
      println("OAuthSpec verifier -> %s" format verifier)
      val access_token = h(host.POST / "access_token" <@ (consumer, request_token, verifier) as_token)
      println("OAuthSpec access_token -> %s" format access_token)
      "1" must_=="1"
    }
  }
}
