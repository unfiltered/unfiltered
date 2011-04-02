package unfiltered.oauth2

import org.specs._

object OAuth2Spec extends Specification with unfiltered.spec.jetty.Served {

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}
  import dispatch._

  System.setProperty("file.encoding", "UTF-8")
  val cli = MockClient("mockclient", "mockserver", "http://localhost:%s/echo" format port)
  val owner = MockResourceOwner("doug")
  def setup = { server =>

    val authProvider = new MockAuthProvider(cli, owner)

    server.context("/oauth") {
      _.filter(OAuth(authProvider.authSvr))
    }
    .context("/echo") {
      _.filter(unfiltered.filter.Planify {
        case UFPath(p) => ResponseString("echo: %s" format p)
      })
    }
  }

  val authorize = host / "oauth" / "authorize"

  val token = host / "oauth" / "token"

  "OAuth2 server authorize step" should {

    "require a response_type" in {
       val (body, headers) = Http(authorize <<? Map(
         "client_id" -> cli.id,
         "redirect_uri" -> cli.redirectURI
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }

    "require a client_id" in {
       val (body, headers) = Http(authorize <<? Map(
         "response_type" -> "code",
         "redirect_uri" -> cli.redirectURI
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }

    "require a redirect_uri" in {
       val (body, headers) = Http(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> cli.id,
         "redirect_uri" -> cli.redirectURI
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }

    "provide a web server flow" in {
       val (body, headers) = Http(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> cli.id
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }

    "provide an implicit flow" in {
       val (body, headers) = Http(authorize <<? Map(
         "response_type" -> "token",
         "client_id" -> cli.id,
         "redirect_uri" -> cli.redirectURI
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }
  }
}
