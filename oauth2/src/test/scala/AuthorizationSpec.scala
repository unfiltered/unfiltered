
package unfiltered.oauth2

import org.specs._

object AuthorizationSpec extends Specification
  with unfiltered.spec.jetty.Served {

  import unfiltered.response._
  import unfiltered.request._
  import unfiltered.request.{Path => UFPath}

  import dispatch._

  import scala.util.parsing.json.JSON

  System.setProperty("file.encoding", "UTF-8")

  val client = MockClient(
    "mockclient", "mockserver", "http://localhost:%s/echo" format port
  )

  val owner = MockResourceOwner("doug")

  def setup = { server =>

    val authProvider = new MockAuthServerProvider(client, owner)

    server.context("/oauth") {
      _.filter(new OAuthorization(authProvider.authSvr))
    }
    .context("/echo") {
      _.filter(unfiltered.filter.Planify {
        case UFPath(p) => ResponseString("echo: %s" format p)
      })
    }
  }

  val authorize = host / "oauth" / "authorize"

  val token = host / "oauth" / "token"

  "Authorization server authorize step" should {

    "require a response_type" in {
       val body = Http(authorize <<? Map(
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri
       ) as_str)
       val json = JSON.parseFull(body)
       JSON.parseFull(body) match {
         case Some(obj) =>
           val map = obj.asInstanceOf[Map[String, Any]]
           map must havePair("error", "invalid_request")
           map must havePair("error_description", "response_type is required")
         case _ => fail("could not parse body")
       }
    }

    "require a client_id" in {
       val body = Http(authorize <<? Map(
         "response_type" -> "code",
         "redirect_uri" -> client.redirectUri
       ) as_str)
       JSON.parseFull(body) match {
         case Some(obj) =>
           val map = obj.asInstanceOf[Map[String, Any]]
           map must havePair("error", "invalid_request")
           map must havePair("error_description", "client_id is required")
         case _ => fail("could not parse body")
       }
    }

    "require a redirect_uri" in {
       val body = Http(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> client.id
       ) as_str)
       JSON.parseFull(body) match {
         case Some(obj) =>
           val map = obj.asInstanceOf[Map[String, Any]]
           map must havePair("error", "invalid_request")
           map must havePair("error_description", "redirect_uri is required")
         case _ => fail("could not parse body")
       }
    }

    "provide a web server flow" in {
       val body = Http(authorize <<? Map(
         "response_type" -> "code",
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri
       ) as_str)
       JSON.parseFull(body) match {
         case Some(obj) =>
           val map = obj.asInstanceOf[Map[String, Any]]
           println(map)
         case _ => fail("could not parse body")
       }
    }

    "provide an implicit flow" in {
       val (body, headers) = Http(authorize <<? Map(
         "response_type" -> "token",
         "client_id" -> client.id,
         "redirect_uri" -> client.redirectUri
       ) >+ { r=> (r as_str, r >:> {h=>h}) })
       println("%s\n%s" format(headers, body))
    }
  }
}
