package unfiltered.kit

import org.specs2.mutable._

import unfiltered.request._

class RoutesSpecJetty
extends Specification
with unfiltered.specs2.jetty.Planned
with RoutesSpec

class RoutesSpecNetty
extends Specification
with unfiltered.specs2.netty.Planned
with RoutesSpec

trait RoutesSpec extends Specification with unfiltered.specs2.Hosted {
  import unfiltered.response._

  def intent[A,B]: unfiltered.Cycle.Intent[A,B] = Routes.specify(
    "/widgets" -> contrivedPass,
    "/widgets" -> widgets,
    "/widgets/:widget_id" -> getWidget,
    "/widgets/:widget_id/sprockets" -> sprockets,
    "/widgets/:widget_id/sprockets/:sprocket_id" -> assembly
  )

  def contrivedPass[A](req: HttpRequest[A], params: Map[String, String]) = {
    Pass
  }

  def widgets[A](req: HttpRequest[A], params: Map[String, String]) = {
    ResponseString("allTheWidgets")
  }

  def getWidget[A](req: HttpRequest[A], params: Map[String, String]) = {
    ResponseString(s"widget:${params("widget_id")}")
  }

  def sprockets[A](req: HttpRequest[A], params: Map[String, String]) = {
    ResponseString(s"sprocketsOf:${params("widget_id")}")
  }

  def assembly[A](req: HttpRequest[A], params: Map[String, String]) = {
    val widgetId :: sprocketId :: Nil =
      ("widget_id" :: "sprocket_id" :: Nil).map(params)
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
