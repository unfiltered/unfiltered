package unfiltered.response

import net.liftweb.json.JsonAST._
import net.liftweb.json.JsonDSL._

object Json {
  val jsonToString = render _ andThen compact _
  
  def apply(json: JObject) =
    new ChainResponse(JsonContent ~> ResponseString(jsonToString(json)))
}
