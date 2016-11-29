package unfiltered.oauth

import com.github.scribejava.core.builder.ServiceBuilder
import com.github.scribejava.core.builder.api.DefaultApi10a
import com.github.scribejava.core.model.{OAuth1RequestToken, OAuthRequest, SignatureType, Verb}
import okhttp3.HttpUrl
import org.specs2.mutable._

object OAuthSpec extends Specification with unfiltered.specs2.jetty.Served {

  import unfiltered.response._

  System.setProperty("file.encoding", "UTF-8")
  //val consumer = Consumer("key", "secret")

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
          val key = "key"
          val secret = "secret"
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
      _.plan(new OAuth(stores) with CustomPaths)
    }
    .plan(Protection(stores))
    .plan(unfiltered.filter.Planify {
      case request =>
        ResponseString(request.underlying.getAttribute(unfiltered.oauth.OAuth.XAuthorizedIdentity) match {
          case null => "unknown user. abort! abort!"
          case id: String => id
        })
    })
  }

  "oauth" should {
    "authorize a valid consumer's request using a HMAC-SHA1 signature with oob workflow" in {
      val service = new ServiceBuilder()
        .signatureType(SignatureType.Header)
        .apiKey("key")
        .apiSecret("secret")
        .build(new DefaultApi10a {
          override def getAccessTokenEndpoint: String = (host / "oauth" / "access").toString

          override def getAuthorizationUrl(requestToken: OAuth1RequestToken): String = {
            (host / "oauth" / "auth").newBuilder().addQueryParameter("oauth_token", requestToken.getToken).build().toString
          }

          override def getRequestTokenEndpoint: String = (host / "oauth" / "requests").toString
        })

      val requestToken = service.getRequestToken
      val VerifierRE = """<p id="verifier">(.+)</p>""".r
      val verifier = http(HttpUrl.parse(service.getAuthorizationUrl(requestToken))).as_string match {
        case VerifierRE(v) => v
        case _ => "?"
      }

      val accessToken = service.getAccessToken(requestToken, verifier)
      val request = new OAuthRequest(Verb.GET, (host / "user").toString, service)
      service.signRequest(accessToken, request)

      val user = request.send().getBody

      user must_== "test_user"
    }
  }
}
