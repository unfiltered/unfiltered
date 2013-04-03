package unfiltered.response

import org.json4s._
import native.JsonMethods._

object Json {
  def jsonToString(json: JValue) = compact(render(json))

  def apply(json: JValue) =
    new ComposeResponse(JsonContent ~> ResponseString(jsonToString(json)))

  def apply(json: JValue, cb: String) =
    new ComposeResponse(JsContent ~> ResponseString("%s(%s)" format(cb, jsonToString(json))))
}
