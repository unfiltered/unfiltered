package unfiltered.response

import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonAST._

object Json {
  val jsonToString = render _ andThen compact _ 
  sealed trait JSerializable[A <: JValue] extends (A => String) {
    def apply(value: A) = jsonToString(value)
  }
  
  implicit object jobject extends JSerializable[JObject]
  implicit object jarray extends JSerializable[JArray]
  implicit object jnull extends JSerializable[JNull.type]
  implicit object jdouble extends JSerializable[JDouble]
  implicit object jint extends JSerializable[JInt]
  implicit object jstring extends JSerializable[JString]
  implicit object jbool extends JSerializable[JBool]
  
  def apply[J <: JValue](json: J)(implicit ser: JSerializable[J]) = 
    new ChainResponse(JsonContent ~> ResponseString(ser(json)))

  def apply[J <: JValue](json: J, cb: String)(implicit ser: JSerializable[J]) = 
    new ChainResponse(JsContent ~> ResponseString("%s(%s)" format(cb, ser(json))))
}
