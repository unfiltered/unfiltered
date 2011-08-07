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

  def setup = { server =>

    trait CustomPaths extends OAuthPaths {
      override val RequestTokenPath = "/requests"
      override val AuthorizationPath = "/auth"
      override val AccessTokenPath = "/access"
    }

    val stores = new MockOAuthStores {
      var tokenMap = scala.collection.mutable.Map.empty[String, unfiltered.oauth.Token]

      override val consumers = new ConsumerStore {
        def get(key: String) = Some(new unfiltered.oauth.Consumer {
          val key = consumer.key
          val secret = consumer.secret
        })
      }

      override val tokens = new DefaultTokenStore {
        override def get(key: String) = tokenMap.get(key)
        override def put(token: unfiltered.oauth.Token) = {
          tokenMap += (token.key -> token)
          token
        }
        override def delete(key: String) = tokenMap -= key
      }
    }

    server.context("/oauth") {
      _.filter(new OAuth(stores) with CustomPaths)
    }
    .filter(Protection(stores))
    .filter(unfiltered.filter.Planify {
      case request =>
        ResponseString(request.underlying.getAttribute(unfiltered.oauth.OAuth.XAuthorizedIdentity) match {
          case null => "unknown user. abort! abort!"
          case id: String => id
        })
    })
  }

  "oauth" should {
    "authorize a valid consumer's request using a HMAC-SHA1 signature with oob workflow" in {
      val h = new Http
      val payload = Map("identité" -> "caché", "identity" -> "hidden", "アイデンティティー" -> "秘密",
        "pita" -> "-._~*")
      println("OAuthSpec consumer -> %s" format consumer)
      val request_token = h(host.POST / "oauth" / "requests" << payload <@(consumer, OAuth.oob) as_token)
      println("OAuthSpec request_token -> %s" format request_token)
      val VerifierRE = """<p id="verifier">(.+)</p>""".r
      val verifier = h(host / "oauth" / "auth" with_token request_token as_str) match {
        case VerifierRE(v) => v
        case _ => "?"
      }
      println("OAuthSpec verifier -> %s" format verifier)
      val access_token = h(host.POST / "oauth" / "access" <@ (consumer, request_token, verifier) as_token)
      println("OAuthSpec access_token -> %s" format access_token)
      val user = h(host / "user" <@(consumer, access_token, verifier) as_str)
      user must_=="test_user"
    }
  }
}
