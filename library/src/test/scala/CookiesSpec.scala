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

  object Foo extends Cookies.Extract("foo", Cookies.value)

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = {
    case UFPath("/") & Cookies(Foo(f)) =>
      ResponseString("foo %s!" format f)

    case UFPath("/") & Cookies(c) =>
      ResponseString("foo who?")

    case POST(UFPath("/save") & Params(p)) =>
      SetCookies(Cookie("foo", p("foo")(0))) ~> Redirect("/")

    case UFPath("/clear") =>
      SetCookies.discarding("foo") ~> Redirect("/")
  }

  "Cookies" should {
    "start with nothing" in {
      http(host as_str) must_=="foo who?"
    }
    "then add a cookie" in {
      http(host.POST / "save" << Map("foo" -> "bar") as_str) must_=="foo bar!"
    }
    "and finally clear it when requested" in {
      // use a stateful http instance to maintain the cookie stash
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
