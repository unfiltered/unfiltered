package unfiltered.kit

import org.specs2.mutable._

import unfiltered.request._

class RoutesRegexSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with RoutesRegexSpec

class RoutesRegexSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with RoutesRegexSpec

trait RoutesRegexSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = Routes.regex(
    "/widgets/?" -> widgets,
    """/widgets/(\d+)""" -> getWidget,
    """/widgets/(\d+)/sprockets""" -> sprockets,
    """/widgets/(\d+)/sprockets/(\d+)""" -> assembly
  )

  def widgets[A](req: HttpRequest[A], groups: List[String]) = {
    ResponseString("allTheWidgets")
  }

  def getWidget[A](req: HttpRequest[A], groups: List[String]) = {
    ResponseString(s"widget:${groups.head}")
  }

  def sprockets[A](req: HttpRequest[A], groups: List[String]) = {
    ResponseString(s"sprocketsOf:${groups.head}")
  }

  def assembly[A](req: HttpRequest[A], groups: List[String]) = {
    val widgetId :: sprocketId :: Nil = groups
    ResponseString(s"${widgetId}:${sprocketId}")
  }

  "Routes.specify" should {
    "match a path" in {
      http(host / "widgets").as_string must_==
        "allTheWidgets"
    }
    "match a parameter" in {
      http(host / "widgets" / "123").as_string must_==
        "widget:123"
    }
    "match a parameter and path" in {
      http(host / "widgets" / "123" / "sprockets").as_string must_==
        "sprocketsOf:123"
    }
    "match two parameters" in {
      http(host / "widgets" / "123" / "sprockets" / "456").as_string must_==
        "123:456"
    }
  }
}
