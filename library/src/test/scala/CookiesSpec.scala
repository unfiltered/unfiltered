package unfiltered.request

import java.time.Instant

import okhttp3.{Cookie, HttpUrl, OkHttpClient}
import org.specs2.mutable._

class CookiesSpecJetty
  extends Specification
    with unfiltered.specs2.jetty.Planned
    with CookiesSpec

class CookiesSpecNetty
  extends Specification
    with unfiltered.specs2.netty.Planned
    with CookiesSpec

trait CookiesSpec extends Specification with unfiltered.specs2.Hosted {

  import scala.jdk.CollectionConverters._

  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.Cookie
  import unfiltered.Cycle.Intent

  def intent[A, B]: Intent[A, B] = {

    case UFPath("/") & Cookies(cs) if cs("foo").isDefined =>
      ResponseString(s"foo ${cs("foo").map(_.value).getOrElse("?")}!")

    case UFPath("/") & Cookies(cs) =>
      ResponseString("foo who?")

    case POST(UFPath("/save") & Params(p)) =>
      SetCookies(Cookie("foo", p("foo")(0))) andThen Redirect("/")

    case UFPath("/clear") =>
      SetCookies.discarding("foo") andThen Redirect("/")

    case UFPath("/multi") & Cookies(cs) if cs("foo").isDefined && cs("baz").isDefined =>
      ResponseString(s"foo ${cs("foo").map(_.value).getOrElse("?")} baz ${cs("baz").map(_.value).getOrElse("?")}!")

    case UFPath("/multi") & Cookies(cs) =>
      ResponseString("who and the what now?")

    case POST(UFPath("/save_multi") & Params(p)) =>
      SetCookies(Cookie("foo", p("foo")(0)), Cookie("baz", p("baz")(0))) andThen Redirect("/multi")

    case UFPath("/clear_multi") & Params(p) =>
      SetCookies.discarding("foo", "baz") andThen Redirect("/")
  }

  "Cookies" should {
    "start with nothing" in {
      http(req(host)).as_string must_== "foo who?"
    }
    "then add a cookie" in {
      withCookieJar{jar =>
        val h = httpWithCookies(jar)
        h(req(host / "save") << Map("foo" -> "bar")).as_string must_== "foo bar!"
      }
    }

    // http://hc.apache.org/httpcomponents-client-ga/tutorial/html/statemgmt.html#d5e746

     "and finally clear it when requested" in {
      withCookieJar { jar =>
        val h = httpWithCookies(jar)
        h(req(host / "save") << Map("foo" -> "bar")).as_string must_== "foo bar!"
        val someCookies = jar.loadForRequest(host / "save").asScala
        someCookies.size must_== 1
        someCookies.find(_.name == "foo") must beSome
        h(host / "clear").as_string must_== "foo who?"
        val noCookies = jar.loadForRequest(host / "clear").asScala
        noCookies.size must_== 0
        noCookies.find(_.name == "foo") must beNone
      }
    }
    "saves and deletes multiple cookies" in {
      withCookieJar { jar =>
        val h = httpWithCookies(jar)
        h(req(host/ "save_multi") << Map("foo" -> "bar", "baz" -> "boom")).as_string must_== "foo bar baz boom!"
        val someCookies = jar.loadForRequest(host/ "save_multi").asScala
        someCookies.size must_== 2
        someCookies.find(_.name == "foo") must beSome
        someCookies.find(_.name == "baz") must beSome
        h(host / "clear_multi").as_string must_== "foo who?"
        val noCookies = jar.loadForRequest(host / "clear").asScala
        noCookies.size must_== 0
        noCookies.find(_.name == "foo") must beNone
        noCookies.find(_.name == "baz") must beNone
      }
    }
  }

  def withCookieJar[T](f: okhttp3.CookieJar => T): T = {
    val jar = new MemoryJar
    try { f(jar) }
    finally { jar.clear() }
  }

  def httpWithCookies(jar: okhttp3.CookieJar): okhttp3.Request => Response = { req =>
    requestWithNewClient(req, new OkHttpClient.Builder().cookieJar(jar))
  }
}

class MemoryJar extends okhttp3.CookieJar {

  import scala.jdk.CollectionConverters._

  val jar = collection.concurrent.TrieMap[String, List[Cookie]]()

  override def saveFromResponse(url: HttpUrl, cookies: java.util.List[Cookie]): Unit = {
    val list = cookies.asScala.flatMap(p =>
      if (Instant.ofEpochMilli(p.expiresAt()).isAfter(Instant.now())) List(p) else Nil
    )
    if (list.isEmpty) jar -= url.host() else {
      jar += (url.host() -> list.toList)
    }
  }

  override def loadForRequest(url: HttpUrl): java.util.List[Cookie] = {
    new java.util.ArrayList(jar.getOrElse(url.host(), Nil).asJavaCollection)
  }

  def clear(): Unit = jar.clear()
}
