package unfiltered.kit

import org.specs2.mutable._

import unfiltered.request._

object RoutesStartsWithSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with RoutesStartsWithSpec

object RoutesStartsWithSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with RoutesStartsWithSpec

trait RoutesStartsWithSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = Routes.startsWith(
    "/the" -> theFunction,
    "/things/" -> things
  )

  def theFunction[A](req: HttpRequest[A], rest: String) = {
    ResponseString(rest)
  }

  def things[A](req: HttpRequest[A], rest: String) = {
    ResponseString(rest)
  }

  "Routes.specify" should {
    "match the beginning" in {
      http(host / "the_best_stuff.html").as_string must_==
        "_best_stuff.html"
    }
    "match a thing" in {
      http(host / "things" / "thingone.html").as_string must_==
        "thingone.html"
    }
  }
}
