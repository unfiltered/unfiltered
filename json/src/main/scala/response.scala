package unfiltered.response

import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

object Json {
  val jsonToString = render _ andThen compact _

  def apply(json: JValue) =
    new ChainResponse(JsonContent ~> ResponseString(jsonToString(json)))

  def apply(json: JValue, cb: String) =
    new ChainResponse(JsContent ~> ResponseString("%s(%s)" format(cb, jsonToString(json))))
}
