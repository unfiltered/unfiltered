package unfiltered.oauth2

import org.specs._

object CustomAuthorizationSpec
  extends Specification
  with unfiltered.spec.jetty.Served {

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  val client = MockClient(
    "mockclient", "mockserver",
    "http://localhost:%s/echo" format port
  )

  val owner = MockResourceOwner("doug")
  val password = "mockuserspassword"

  def setup = { server =>
    val authProvider = new MockAuthServerProvider(client, owner)
    server.context("/oauth") {
      _.filter(new OAuthorization(authProvider.auth) {
        // this authorization module configuration 
        // does not support a client credentials flow
        override def onClientCredentials(
          clientId: String, clientSecret: String,
          scope: Seq[String]) =
            Forbidden ~> ResponseString("client_credentials not supported")
      })
    }
    .context("/echo") {
      _.filter(unfiltered.filter.Planify {
        case UFPath(p) => ResponseString("echo: %s" format p)
      })
    }
  }

  val token = host / "oauth" / "token"

  "A customized OAuthorization configuration" should {
    "permit overrided flow behavior" in {

      val body = Http.when(_ == 403)(token << Map(
         "grant_type" -> "client_credentials",
         "client_id" -> client.id,
         "client_secret" -> client.secret,
         "redirect_uri" -> client.redirectUri
      ) as_str )
      body must_== "client_credentials not supported"
    }
  }
}
