package unfiltered.kit

import org.specs._

import unfiltered.request._

object RoutesSpecJetty
extends unfiltered.spec.jetty.Planned
with RoutesSpec

object RoutesSpecNetty
extends unfiltered.spec.netty.Planned
with RoutesSpec

trait RoutesSpec extends unfiltered.spec.Hosted {
  import unfiltered.response._
  import unfiltered.request.{Path => UFPath}
  import unfiltered.Cookie

  import dispatch._

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
    ResponseString("widget:%s".format(params("widget_id")))
  }

  def sprockets[A](req: HttpRequest[A], params: Map[String, String]) = {
    ResponseString("sprocketsOf:%s".format(params("widget_id")))
  }

  def assembly[A](req: HttpRequest[A], params: Map[String, String]) = {
    val widgetId :: sprocketId :: Nil =
      ("widget_id" :: "sprocket_id" :: Nil).map(params)
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
