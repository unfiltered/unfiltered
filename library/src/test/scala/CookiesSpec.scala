package unfiltered.request

import org.specs._

object CookiesSpecJetty
extends unfiltered.spec.jetty.Planned
with CookiesSpec

object CookiesSpecNetty
extends unfiltered.spec.netty.Planned
with CookiesSpec

trait CookiesSpec extends unfiltered.spec.Hosted {
  import scala.collection.JavaConversions._

  import unfiltered.response._
  import unfiltered.request.{ Path => UFPath }
  import unfiltered.Cookie
  import unfiltered.Cycle.Intent
  import QParams._

  import dispatch._

  import org.apache.http.client.CookieStore
  import org.apache.http.impl.client.{ AbstractHttpClient, BasicCookieStore }
  import org.apache.http.client.params.{ ClientPNames, CookiePolicy }

  def intent[A,B]: Intent[A,B] = {

    case UFPath("/") & Cookies(cs) if(cs("foo").isDefined) =>
      ResponseString("foo %s!" format(cs("foo").map(_.value).getOrElse("?")))

    case UFPath("/") & Cookies(cs) =>
      ResponseString("foo who?")

    case POST(UFPath("/save") & Params(p)) =>
      SetCookies(Cookie("foo", p("foo")(0))) ~> Redirect("/")

    case UFPath("/clear") =>
      SetCookies.discarding("foo") ~> Redirect("/")

    case UFPath("/multi") & Cookies(cs) if(cs("foo").isDefined && cs("baz").isDefined) =>
      ResponseString("foo %s baz %s!" format(
        cs("foo").map(_.value).getOrElse("?"),
        cs("baz").map(_.value).getOrElse("?")
      ))

    case UFPath("/multi") & Cookies(cs) =>
      ResponseString("who and the what now?")

    case POST(UFPath("/save_multi") & Params(p)) =>
      SetCookies(Cookie("foo", p("foo")(0)), Cookie("baz", p("baz")(0))) ~> Redirect("/multi")

    case UFPath("/clear_multi") & Params(p) =>
      SetCookies.discarding("foo", "baz") ~> Redirect("/")
  }

  "Cookies" should {
    "start with nothing" in {
      http(host as_str) must_=="foo who?"
    }
    "then add a cookie" in {
      http(host.POST / "save" << Map("foo" -> "bar") as_str) must_=="foo bar!"
    }

    // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/statemgmt.html#d5e746

    "and finally clear it when requested" in {
      withCookieJar { jar =>
        val h = httpWithCookies(jar)
        try {
          h(host.POST / "save" << Map("foo" -> "bar") as_str) must_=="foo bar!"
          val someCookies = jar.getCookies
          someCookies.size must_== 1
          someCookies.find(_.getName == "foo") must beSomething
          h(host / "clear" as_str) must_=="foo who?"
          val noCookies = jar.getCookies
          noCookies.size must_== 0
          noCookies.find(_.getName == "foo") mustNot beSomething
        } finally {
          h.shutdown()
        }
      }
    }
    "saves and deletes multiple cookies" in {
      withCookieJar { jar =>
        val h = httpWithCookies(jar)
        try {
          h(host.POST / "save_multi" << Map("foo" -> "bar", "baz" -> "boom") as_str) must_== "foo bar baz boom!"
          val someCookies = jar.getCookies
          someCookies.size must_== 2
          someCookies.find(_.getName == "foo") must beSomething
          someCookies.find(_.getName == "baz") must beSomething
          h(host / "clear_multi" as_str) must_== "foo who?"
          val noCookies = jar.getCookies
          noCookies.size must_== 0
          noCookies.find(_.getName == "foo") mustNot beSomething
          noCookies.find(_.getName == "baz") mustNot beSomething
        } finally {
          h.shutdown()
        }
      }
    }
  }

  def withCookieJar[T](f: CookieStore => T): T = {
    val jar = new BasicCookieStore
    try { f(jar) }
    finally { jar.clear }
  }

  def httpWithCookies(jar: CookieStore) = 
     new Http {
        override def make_client =
          super.make_client match {
            case c: AbstractHttpClient =>
              //c.getParams.setParameter(ClientPNames.COOKIE_POLICY, CookiePolicy.RFC_2965)
              c.setCookieStore(jar)
              c
            case hc => /*sys.*/error("expected abstract http client but found %s" format hc.getClass)
          }
      }
}
