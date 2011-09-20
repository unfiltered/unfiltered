package unfiltered.response

import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._
import net.liftweb.json.Printer.compact

object Json {
  def jsonToString(json: JValue) = compact(render(json))

  def apply(json: JValue) =
    new ComposeResponse(JsonContent ~> ResponseString(jsonToString(json)))

  def apply(json: JValue, cb: String) =
    new ComposeResponse(JsContent ~> ResponseString("%s(%s)" format(cb, jsonToString(json))))
}
