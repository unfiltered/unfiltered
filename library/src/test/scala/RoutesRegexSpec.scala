package unfiltered.kit

import org.specs._

import unfiltered.request._

import scala.util.matching.Regex

object RoutesRegexSpecJetty
extends unfiltered.spec.jetty.Planned
with RoutesRegexSpec

object RoutesRegexSpecNetty
extends unfiltered.spec.netty.Planned
with RoutesRegexSpec

trait RoutesRegexSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.Cookie

  import dispatch._

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
    ResponseString("widget:%s".format(groups.head))
  }

  def sprockets[A](req: HttpRequest[A], groups: List[String]) = {
    ResponseString("sprocketsOf:%s".format(groups.head))
  }

  def assembly[A](req: HttpRequest[A], groups: List[String]) = {
    val widgetId :: sprocketId :: Nil = groups
    ResponseString("%s:%s".format(widgetId, sprocketId))
  }

  "Routes.specify" should {
    "match a path" in {
      http(host / "widgets" as_str) must_==
        "allTheWidgets"
    }
    "match a parameter" in {
      http(host / "widgets" / "123" as_str) must_==
        "widget:123"
    }
    "match a parameter and path" in {
      http(host / "widgets" / "123" / "sprockets" as_str) must_==
        "sprocketsOf:123"
    }
    "match two parameters" in {
      http(host / "widgets" / "123" / "sprockets" / "456" as_str) must_==
        "123:456"
    }
  }
}
