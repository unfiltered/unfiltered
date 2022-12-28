package unfiltered.response

import org.json4s._
import native.JsonMethods._

object Json {
  def jsonToString(json: JValue): String = compact(render(json))

  def apply[A](json: JValue): ComposeResponse[A] =
    new ComposeResponse[A](JsonContent ~> ResponseString(jsonToString(json)))

  def apply[A](json: JValue, cb: String): ComposeResponse[A] =
    new ComposeResponse[A](JsContent ~> ResponseString(s"${cb}(${jsonToString(json)})"))
}
