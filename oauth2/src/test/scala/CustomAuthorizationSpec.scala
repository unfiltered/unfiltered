package unfiltered.oauth2

import org.specs2.mutable._

object CustomAuthorizationSpec
  extends Specification
  with unfiltered.specs2.jetty.Served {

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  val client = MockClient(
    "mockclient", "mockserver",
    "http://localhost:%s/echo" format port
  )

  val owner = MockResourceOwner("doug")
  val password = "mockuserspassword"

  def setup = { server =>
    val authProvider = new MockAuthServerProvider(client, owner)
    server.context("/oauth") {
      _.plan(new OAuthorization(authProvider.auth) {
        // this authorization module configuration
        // does not support a client credentials flow
        override def onClientCredentials(
          clientId: String, clientSecret: String,
          scope: Seq[String]) =
            Forbidden ~> ResponseString("client_credentials not supported")
      })
    }
    .context("/echo") {
      _.plan(unfiltered.filter.Planify {
        case UFPath(p) => ResponseString("echo: %s" format p)
      })
    }
  }

  val token = host / "oauth" / "token"

  "A customized OAuthorization configuration" should {
    "permit overrided flow behavior" in {

      val resp = httpx(req(token) << Map(
         "grant_type" -> "client_credentials",
         "client_id" -> client.id,
         "client_secret" -> client.secret,
         "redirect_uri" -> client.redirectUri
      ))

      resp.code must_== 403
      resp.as_string must_== "client_credentials not supported"
    }
  }
}
