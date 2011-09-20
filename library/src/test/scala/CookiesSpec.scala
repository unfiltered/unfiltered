
package unfiltered.request

import org.specs._

object CookiesSpecJetty
extends unfiltered.spec.jetty.Planned
with CookiesSpec

object CookiesSpecNetty
extends unfiltered.spec.netty.Planned
with CookiesSpec

trait CookiesSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.Cookie
  import QParams._

  import dispatch._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case UFPath("/") & Cookies(cookies) => ResponseString(cookies("foo") match {
      case Some(Cookie(name, value, domain, path, maxAge, secure)) => value match {
        case "" => "foo who?"
        case foo => "foo %s!" format foo
      }
      case _ => "foo who?"
    })

    case POST(UFPath("/save") & Params(p)) =>
      ResponseCookies(Cookie("foo", p("foo")(0))) ~> Redirect("/")

    case UFPath("/clear") =>
      // clearing cookie value is the same as deleting in http
      ResponseCookies(Cookie("foo", "")) ~> Redirect("/")
  }

  "Cookies" should {
    "start with nothing" in {
      http(host as_str) must_=="foo who?"
    }
    "then add a cookie" in {
      http(host.POST / "save" << Map("foo" -> "bar") as_str) must_=="foo bar!"
    }
    "and finally clear it when requested" in {
      // statefull http so we need to ref the instance
      val h = new Http
      try {
        h(host.POST / "save" << Map("foo" -> "bar") as_str) must_=="foo bar!"
        h(host / "clear" as_str) must_=="foo who?"
      } finally {
        h.shutdown()
      }

    }
  }
}
